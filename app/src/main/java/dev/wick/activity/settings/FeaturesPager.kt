package dev.wick.activity.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Chevron_right
import com.composables.icons.materialsymbols.outlined.Check_circle
import com.composables.icons.materialsymbols.outlined.Close
import com.composables.icons.materialsymbols.outlined.Fiber_new
import com.composables.icons.materialsymbols.outlined.Search
import dev.wick.R
import dev.wick.features.core.BaseFeature
import dev.wick.features.core.FeaturesProvider
import dev.wick.features.core.NewFeatures
import dev.wick.features.core.SwitchFeature
import dev.wick.features.core.featureCategoryComparator
import dev.wick.i18n.LocalWeKitLocalizedContext
import dev.wick.i18n.WeKitLocaleController
import dev.wick.data.KvStore
import dev.wick.ui.content.m3.BaseWidget
import dev.wick.ui.content.m3.ExpressiveBackButton
import dev.wick.ui.content.m3.SegmentedColumn
import dev.wick.ui.content.m3.lazySegmentedItems
import java.text.Collator
import java.util.Locale
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.wick.ui.content.m3.CornerRadius
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.BorderStroke
import dev.wick.ui.content.m3.CategoryPalette


/**
 * Current switch state of [item], read straight from MMKV using the feature's own
 * [SwitchFeature.defaultEnabled] — the very default `SwitchFeature.startup()` applies.
 */
@Composable
private fun featureChecked(item: BaseFeature): Boolean {
    val revision = FeatureCategoryState.revision
    return remember(item.technicalId, revision) {
        KvStore.getBoolOrDef(item.technicalId, (item as? SwitchFeature)?.defaultEnabled == true)
    }
}

// ---------------------------------------------------------------------------
//  Page 1 — Features (search bar + category list)
// ---------------------------------------------------------------------------

@Composable
fun FeaturesPager(onOpenCategory: (String) -> Unit) {
    val context = LocalWeKitLocalizedContext.current
    val resolvedLocale = WeKitLocaleController.resolvedLocale
    val revision = FeatureCategoryState.revision
    var query by remember { mutableStateOf("") }
    val searching = query.isNotBlank()

    val featureNameCollator = remember(resolvedLocale) {
        Collator.getInstance(Locale.forLanguageTag(resolvedLocale.androidTag))
    }
    val searchableItems = remember(resolvedLocale) {
        FeaturesProvider.ALL_FEATURES
            .filterIsInstance<SwitchFeature>()
            .sortedWith { first, second ->
                featureNameCollator.compare(first.localizedName(context), second.localizedName(context))
            }
    }
    val filteredItems = remember(query, resolvedLocale, revision) {
        if (!searching) emptyList()
        else searchableItems.filter {
            it.localizedName(context).contains(query, ignoreCase = true) ||
                it.localizedDescription(context).contains(query, ignoreCase = true)
        }
    }

    // A back press while searching clears the query first (after the IME's own
    // back has dismissed the keyboard) rather than exiting the module settings.
    BackHandler(enabled = searching) { query = "" }

    M3ListScaffold(title = stringResource(R.string.nav_features)) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .padding(top = 12.dp, start = 16.dp, end = 16.dp)
                    .fillMaxWidth(),
                label = { Text(stringResource(R.string.features_search_hint)) },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = MaterialSymbols.Outlined.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingIcon = {
                    if (searching) {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                imageVector = MaterialSymbols.Outlined.Close,
                                contentDescription = stringResource(R.string.features_clear_search),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        }

        if (searching) {
            // Search results replace the category list while a query is active
            if (filteredItems.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.features_no_results),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                item { Spacer(Modifier.height(12.dp)) }
                lazySegmentedItems(filteredItems, key = { it.technicalId }) { feature ->
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        FeatureRow(
                            item = feature,
                            checked = featureChecked(feature),
                            onCheckedChange = {},
                        )
                    }
                }
            }
        } else {
            item {
                SegmentedColumn(
                    modifier = Modifier.padding(top = 12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    item(key = NEW_FEATURES_CATEGORY) {
                        BaseWidget(
                            icon = MaterialSymbols.Outlined.Fiber_new,
                            title = stringResource(featureCategoryTitleRes(NEW_FEATURES_CATEGORY)),
                            description = stringResource(
                                R.string.features_new_summary,
                                NewFeatures.WINDOW_DAYS,
                                FeatureCategoryState.newItems.size,
                            ),
                            onClick = { onOpenCategory(NEW_FEATURES_CATEGORY) },
                            trailingContent = {
                                Icon(
                                    imageVector = MaterialSymbols.Outlined.Chevron_right,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                        )
                    }
                    item(key = ENABLED_FEATURES_CATEGORY) {
                        val enabledCount = FeatureCategoryState.enabledItems().size
                        BaseWidget(
                            icon = MaterialSymbols.Outlined.Check_circle,
                            title = stringResource(featureCategoryTitleRes(ENABLED_FEATURES_CATEGORY)),
                            description = stringResource(R.string.feature_category_enabled_summary, enabledCount),
                            onClick = { onOpenCategory(ENABLED_FEATURES_CATEGORY) },
                            trailingContent = {
                                Icon(
                                    imageVector = MaterialSymbols.Outlined.Chevron_right,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
            // Two-column category grid. Replaces the previous one-row-per-category list: a grid of
            // tiles reads as an app launcher rather than a settings list, which is the point —
            // the fork's identity should not look like the stock module settings page.
            item {
                // Counts are recomputed whenever a toggle changes (the surrounding screen already
                // re-reads FeatureCategoryState.revision), so the badges stay live.
                val countsByCategory = FeatureCategoryState.enabledCountByCategory()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FEATURE_CATEGORIES.chunked(2).forEach { rowItems ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            rowItems.forEach { category ->
                                CategoryTile(
                                    category = category,
                                    enabledCount = countsByCategory[category.id] ?: 0,
                                    onClick = { onOpenCategory(category.id) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            // Odd count: keep the last tile half-width instead of stretching it.
                            if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(CONTENT_BOTTOM_INSET)) }
    }
}

// ---------------------------------------------------------------------------
//  Category tile (2-column grid)
// ---------------------------------------------------------------------------

/**
 * One tile in the category grid.
 *
 * Each category carries its own accent (see [CategoryPalette]) so the grid reads as a set of
 * illustrated cards rather than a uniform list: tinted icon chip, matching hairline border, and a
 * count badge. The border is what separates this from a plain Material card — a colored outline
 * on a pastel fill is the visual language 2D/launcher UIs use.
 */
@Composable
private fun CategoryTile(
    category: FeatureCategory,
    enabledCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fill = CategoryPalette.fillFor(category.id)
    val ink = CategoryPalette.inkFor(category.id)

    Surface(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(CornerRadius),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.5.dp, ink.copy(alpha = 0.22f)),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(fill),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = null,
                        tint = ink,
                        modifier = Modifier.size(24.dp),
                    )
                }
                if (enabledCount > 0) {
                    // Count badge: the count is the reason to open a tile, so it sits beside the
                    // icon rather than at the far edge where it competes with the border.
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(ink.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = enabledCount.toString(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ink,
                        )
                    }
                }
            }
            Text(
                text = stringResource(category.titleRes),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ---------------------------------------------------------------------------
//  Category detail (replaces CategorySettingsScreen)
// ---------------------------------------------------------------------------

@Composable
fun CategoryDetailScreen(categoryId: String, onBack: () -> Unit) {
    val context = LocalWeKitLocalizedContext.current
    val resolvedLocale = WeKitLocaleController.resolvedLocale
    val revision = FeatureCategoryState.revision
    val featureNameCollator = remember(resolvedLocale) {
        Collator.getInstance(Locale.forLanguageTag(resolvedLocale.androidTag))
    }
    val items = remember(categoryId, resolvedLocale, revision) {
        when (categoryId) {
            NEW_FEATURES_CATEGORY -> FeatureCategoryState.newItems
            ENABLED_FEATURES_CATEGORY -> FeatureCategoryState.enabledItems()
            else -> FeaturesProvider.ALL_FEATURES
                .filter { categoryId in it.categoryIds }
                .sortedWith(
                    featureCategoryComparator { first, second ->
                        featureNameCollator.compare(
                            first.localizedName(context),
                            second.localizedName(context),
                        )
                    },
                )
        }
    }

    M3ListScaffold(
        title = stringResource(featureCategoryTitleRes(categoryId)),
        navigationIcon = { ExpressiveBackButton(onClick = onBack) },
    ) {
        if (items.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.features_category_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            item { Spacer(Modifier.height(12.dp)) }
            lazySegmentedItems(items, key = { it.technicalId }) { feature ->
                Column(Modifier.padding(horizontal = 16.dp)) {
                    FeatureRow(
                        item = feature,
                        checked = featureChecked(feature),
                        onCheckedChange = {},
                    )
                }
            }
        }

        item { Spacer(Modifier.height(CONTENT_BOTTOM_INSET)) }
    }
}
