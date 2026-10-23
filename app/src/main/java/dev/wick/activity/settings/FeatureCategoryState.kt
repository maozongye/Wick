package dev.wick.activity.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import dev.wick.features.core.BaseFeature
import dev.wick.features.core.ClickableFeature
import dev.wick.features.core.FeaturesProvider
import dev.wick.features.core.NewFeatures
import dev.wick.features.core.SwitchFeature
import dev.wick.data.KvStore

object FeatureCategoryState {
    var revision by mutableIntStateOf(0)
        private set

    val newItems: List<BaseFeature> by lazy {
        val visibleCategories = FEATURE_CATEGORIES.mapTo(mutableSetOf()) { it.id }
        FeaturesProvider.ALL_FEATURES.associateBy { it.technicalId }.values
            .mapNotNull { item ->
                FeaturesProvider.SOURCE_KEY_BY_FEATURE[item]
                    ?.let(NewFeatures.ADDED_AT_BY_SOURCE_KEY::get)
                    ?.let { addedAt -> item to addedAt }
            }
            .filter { (item, _) -> item.categoryIds.any { it in visibleCategories } }
            .sortedWith(
                compareByDescending<Pair<BaseFeature, Long>> { it.second }
                    .thenBy { it.first.technicalId },
            )
            .map { (item, _) -> item }
    }

    fun enabledItems(): List<SwitchFeature> =
        FeaturesProvider.ALL_FEATURES
            .associateBy { it.technicalId }
            .values
            .filterIsInstance<SwitchFeature>()
            .filter { feature ->
                KvStore.getBoolOrDef(feature.technicalId, feature.defaultEnabled) ||
                    (feature is ClickableFeature && feature.alwaysEnabled)
            }
            .sortedBy { it.technicalId }

    fun notifyToggleChanged() {
        revision++
    }

    /**
     * Enabled feature count per category id, for the grid tile badges.
     *
     * A feature may declare several categories; it is counted in each one, matching how the
     * category detail screen lists it.
     */
    fun enabledCountByCategory(): Map<String, Int> {
        val counts = HashMap<String, Int>()
        for (feature in enabledItems()) {
            for (categoryId in feature.categoryIds) {
                counts[categoryId] = (counts[categoryId] ?: 0) + 1
            }
        }
        return counts
    }
}
