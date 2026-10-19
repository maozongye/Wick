package dev.ujhhgtg.wekit.data.structured

import dev.ujhhgtg.wekit.data.entity.*
import dev.ujhhgtg.wekit.features.items.AutomationKeywordMode
import dev.ujhhgtg.wekit.features.items.AutomationKeywordRule
import dev.ujhhgtg.wekit.features.items.AutomationTimeRangeRule
import dev.ujhhgtg.wekit.features.items.AutomationToggleRule
import dev.ujhhgtg.wekit.features.items.moments.*
import dev.ujhhgtg.wekit.utils.strings.isGroupChatWxId

/** Decode NULL as inheritance, never as a default-valued override. */
private fun <T> ruleGroup(enabled: Boolean?, vararg fields: Any?, decode: (Boolean) -> T): T? {
    if (enabled == null) {
        require(fields.all { it == null }) { "Inherited rule has stored scalar values" }
        return null
    }
    require(fields.all { it != null }) { "Stored rule group is incomplete" }
    return decode(enabled)
}

private fun timeRule(enabled: Boolean?, start: Int?, end: Int?): AutomationTimeRangeRule? =
    ruleGroup(enabled, start, end) { AutomationTimeRangeRule(it, start!!, end!!) }

private fun keywordRule(enabled: Boolean?, mode: String?, regex: String?, ignoreCase: Boolean?, strings: List<String>): AutomationKeywordRule? {
    require(enabled != null || strings.isEmpty()) { "Inherited keyword rule has stored keywords" }
    return ruleGroup(enabled, mode, regex, ignoreCase) {
        AutomationKeywordRule(it, AutomationKeywordMode.valueOf(mode!!), strings, regex!!, ignoreCase!!)
    }
}

fun validateMomentScope(featureKind: String, scopeType: String, ownerId: String) {
    require(featureKind == "LIKE" || featureKind == "REPOST") { "Unknown moment automation feature" }
    require(when (scopeType) {
        "GLOBAL" -> ownerId.isEmpty()
        "CONTACT" -> ownerId.isNotEmpty()
        else -> false
    }) { "Invalid moment rule scope" }
}

fun MomentAutomationRuleSet.asOverrides() = MomentAutomationOverrides(
    process = process,
    action = action,
    mode = mode,
    interval = interval,
    timeRange = timeRange,
    keyword = keyword,
    contentType = contentType,
    maximumAge = maximumAge,
)

fun MomentAutomationOverrides.toEntity(featureKind: String, scopeType: String, ownerId: String): MomentAutomationRuleEntity {
    validateMomentScope(featureKind, scopeType, ownerId)
    return MomentAutomationRuleEntity(
        featureKind = featureKind,
        scopeType = scopeType,
        ownerId = ownerId,
        processEnabled = process?.enabled,
        actionEnabled = action?.enabled,
        actionValue = action?.action?.name,
        modeEnabled = mode?.enabled,
        modeValue = mode?.mode?.name,
        intervalEnabled = interval?.enabled,
        intervalMilliseconds = interval?.milliseconds,
        timeEnabled = timeRange?.enabled,
        timeStartMinute = timeRange?.startMinute,
        timeEndMinute = timeRange?.endMinute,
        keywordEnabled = keyword?.enabled,
        keywordMode = keyword?.mode?.name,
        keywordRegex = keyword?.regex,
        keywordIgnoreCase = keyword?.ignoreCase,
        contentTypeEnabled = contentType?.enabled,
        maximumAgeEnabled = maximumAge?.enabled,
        maximumAgeHours = maximumAge?.maximumHours,
    )
}

fun MomentAutomationRuleEntity.toOverrides(keywords: List<MomentAutomationKeywordEntity>, contentTypes: List<MomentAutomationContentTypeEntity>): MomentAutomationOverrides {
    validateMomentScope(featureKind, scopeType, ownerId)
    require(contentTypeEnabled != null || contentTypes.isEmpty()) { "Inherited content-type rule has stored types" }
    return MomentAutomationOverrides(
        process = processEnabled?.let(::AutomationToggleRule),
        action = ruleGroup(actionEnabled, actionValue) { MomentActionRule(it, MomentAutomationAction.valueOf(actionValue!!)) },
        mode = ruleGroup(modeEnabled, modeValue) { MomentModeRule(it, MomentAutomationMode.valueOf(modeValue!!)) },
        interval = ruleGroup(intervalEnabled, intervalMilliseconds) { MomentIntervalRule(it, intervalMilliseconds!!) },
        timeRange = timeRule(timeEnabled, timeStartMinute, timeEndMinute),
        keyword = keywordRule(keywordEnabled, keywordMode, keywordRegex, keywordIgnoreCase, keywords.sortedBy { it.position }.map { it.text }),
        contentType = contentTypeEnabled?.let { MomentTypeRule(it, contentTypes.map { type -> type.typeId }.toSet()) },
        maximumAge = ruleGroup(maximumAgeEnabled, maximumAgeHours) { MomentAgeRule(it, maximumAgeHours!!) },
    )
}

fun MomentAutomationOverrides.asGlobalRules() = MomentAutomationRuleSet(
    process = requireNotNull(process) { "Global process rule is missing" },
    action = requireNotNull(action) { "Global action rule is missing" },
    mode = requireNotNull(mode) { "Global mode rule is missing" },
    interval = requireNotNull(interval) { "Global interval rule is missing" },
    timeRange = requireNotNull(timeRange) { "Global timeRange rule is missing" },
    keyword = requireNotNull(keyword) { "Global keyword rule is missing" },
    contentType = requireNotNull(contentType) { "Global contentType rule is missing" },
    maximumAge = requireNotNull(maximumAge) { "Global maximumAge rule is missing" },
)
