package dev.wick.data.structured

import dev.wick.data.entity.MomentAutomationContentTypeEntity
import dev.wick.data.entity.MomentAutomationKeywordEntity
import dev.wick.features.items.AutomationKeywordMode
import dev.wick.features.items.AutomationKeywordRule
import dev.wick.features.items.moments.MomentActionRule
import dev.wick.features.items.moments.MomentAgeRule
import dev.wick.features.items.moments.MomentAutomationAction
import dev.wick.features.items.moments.MomentAutomationOverrides
import dev.wick.features.items.moments.MomentIntervalRule
import dev.wick.features.items.moments.MomentTypeRule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Round-trip coverage for the moment-automation half of [AutomationRuleCodec].
 *
 * The red-packet and auto-transfer halves were removed together with those features. Their
 * tables remain in the schema so an older build can still read its data, but nothing in this
 * build encodes or decodes that data any more.
 */
class AutomationRuleCodecTest {

    @Test
    fun `repost retains hidden action and distinguishes inherited disabled and empty type sets`() {
        val base = MomentAutomationOverrides(
            action = MomentActionRule(false, MomentAutomationAction.UNLIKE),
            interval = MomentIntervalRule(false, "000"),
            keyword = AutomationKeywordRule(false, AutomationKeywordMode.REGEX, listOf("a", "a", ""), "x+"),
            maximumAge = MomentAgeRule(false, ""),
        )
        val keywords = base.keyword!!.strings.mapIndexed { index, text ->
            MomentAutomationKeywordEntity("REPOST", "CONTACT", "owner", index, text)
        }
        listOf(
            null,
            MomentTypeRule(false, emptySet()),
            MomentTypeRule(true, emptySet()),
            MomentTypeRule(false, setOf(1, 54)),
        ).forEach { typeRule ->
            val input = base.copy(contentType = typeRule)
            val types = typeRule?.typeIds.orEmpty().map {
                MomentAutomationContentTypeEntity("REPOST", "CONTACT", "owner", it)
            }
            assertEquals(input, input.toEntity("REPOST", "CONTACT", "owner").toOverrides(keywords, types))
        }
    }
}
