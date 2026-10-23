package dev.wick.features.items.system

import android.content.ContentValues
import dev.wick.R
import dev.wick.features.api.core.WeDatabaseListenerApi
import dev.wick.features.core.ApiFeature
import dev.wick.features.core.FeatureCategoryIds
import dev.wick.features.core.FeaturesProvider
import dev.wick.utils.RiskControlGate
import dev.wick.utils.RiskControlLevel
import dev.wick.utils.RiskControlSignal
import dev.wick.utils.WeLogger

/**
 * Feeds WeChat's risk-control signals into [RiskControlGate].
 *
 * WeChat pushes its warnings as system messages (type 10000) and service notices
 * (type 318767153). Both land in the `message` table, which [WeDatabaseListenerApi] already
 * exposes. The matcher is deliberately broad — any hit on these words means the account is
 * under active scrutiny, which is exactly when every automation feature must stop.
 */
object RiskControlCircuitBreaker : ApiFeature(), WeDatabaseListenerApi.IInsertListener {

    override val technicalId = "风控熔断器"
    override val nameRes = R.string.feature_we_database_listener_api_name
    override val categoryIds = listOf(FeatureCategoryIds.API)

    private const val TAG = "RiskControl"

    private val riskPhrases = listOf(
        "外挂",
        "非官方客户端",
        "模拟器",
        "被限制登录",
        "被封",
        "安全提醒",
        "违规",
    )

    override fun onEnable() {
        // Mirrors an L3 restriction into SafeMode regardless of which signal source fired it
        // (message-table hints here, NetScene -24 through WeContactApi).
        RiskControlGate.onCooldownEnter = {
            if (!SafeMode.isEnabled) SafeMode.setEnabled(true)
        }
        // L2+ also revokes hooks that are already live. Refusing future enable() calls alone
        // leaves the automation hooks installed — precisely the surface a detector scans for.
        RiskControlGate.onLevelChanged = { level -> revokeAutomationIfThrottled(level) }
        revokeAutomationIfThrottled(RiskControlGate.level)
        WeDatabaseListenerApi.addListener(this)
    }

    override fun onDisable() {
        RiskControlGate.onCooldownEnter = null
        RiskControlGate.onLevelChanged = null
        WeDatabaseListenerApi.removeListener(this)
    }

    /**
     * Disables every feature that declares [dev.wick.features.core.BaseFeature.automationRisk].
     *
     * Deliberately one-way: re-enabling is left to the next WeChat start (or an explicit user
     * toggle) rather than happening automatically when a cooldown expires. Re-attaching
     * automation the moment a timer runs out would resume the exact behaviour that produced
     * the signal.
     */
    private fun revokeAutomationIfThrottled(level: RiskControlLevel) {
        if (level < RiskControlLevel.L2_THROTTLED) return
        val revoked = FeaturesProvider.ALL_FEATURES
            .filter { it.automationRisk && it.isActive }
            .onEach { feature ->
                runCatching { feature.disable() }
                    .onFailure { WeLogger.e(TAG, "failed to revoke ${feature.technicalPath}", it) }
            }
        if (revoked.isNotEmpty()) {
            WeLogger.w(TAG, "revoked ${revoked.size} automation feature(s) at level $level")
        }
    }

    override fun onInsert(table: String, values: ContentValues) {
        if (table != "message") return
        val type = values.getAsInteger("type") ?: return
        if (type != 10_000 && type != 318_767_153) return
        val content = values.getAsString("content") ?: return
        if (riskPhrases.any { content.contains(it) }) {
            val level = RiskControlGate.record(RiskControlSignal.RISK_HINT)
            WeLogger.w(TAG, "risk-control signal observed (level=$level): ${content.take(80)}")
        }
    }
}
