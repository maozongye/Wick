package dev.wick.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BehaviorPacingTest {

    private class FakeClock(var now: Long) : BehaviorPacing.Clock {
        override fun now(): Long = now
    }

    @Test
    fun `delay stays within the clamp range`() {
        repeat(2000) {
            val delay = BehaviorPacing.nextDelayMs(4_000)
            assertTrue(delay in 1_333..12_000, "delay $delay outside clamp range")
        }
    }

    @Test
    fun `delay distribution is not constant`() {
        val samples = (1..200).map { BehaviorPacing.nextDelayMs(4_000) }.toSet()
        assertTrue(samples.size > 50, "gaps look periodic: ${samples.size} distinct values in 200 draws")
    }

    @Test
    fun `distribution is log-normal rather than uniform`() {
        // Log-normal (sigma 0.4) centred on 4000. For ln(N(ln 4000, 0.4)):
        //   P(x < 2000)  = P(z < -1.73) ~ 4%
        //   P(x > 6000)  = P(z > 1.01)  ~ 16%
        //   middle band  ~ 80% minus the clamped remainder; observed ~64%/24% with this clamp.
        // A uniform draw over [1333, 12000] would give 6%/44%/50% instead, so the assertions
        // below distinguish the two shapes with wide margins either way.
        val samples = (1..6000).map { BehaviorPacing.nextDelayMs(4_000) }
        val low = samples.count { it < 2_000 }
        val mid = samples.count { it in 2_000..6_000 }
        val high = samples.count { it > 6_000 }
        assertTrue(mid > 3_300, "expected the bulk near the median, got $mid/6000")
        assertTrue(low < 900, "left tail should be thin, got $low/6000")
        assertTrue(high < 2_100, "right tail should stay well under uniform's, got $high/6000")
        assertTrue(high > low, "log-normal tail must run right, not left: low=$low high=$high")
        assertTrue(samples.toSet().size > 1_000, "gaps look quantised: ${samples.toSet().size} distinct")
    }

    @Test
    fun `median is preserved on average`() {
        var sum = 0.0
        val n = 3000
        repeat(n) { sum += BehaviorPacing.nextDelayMs(4_000) }
        val mean = sum / n
        assertTrue(mean in 3_200.0..5_600.0, "mean drifted from median: $mean")
    }

    @Test
    fun `reserve enforces a sliding window`() {
        val clock = FakeClock(0)
        BehaviorPacing.clock = clock
        try {
            BehaviorPacing.resetForTest()
            repeat(3) { assertTrue(BehaviorPacing.reserve("t", 3, 10_000)) }
            assertFalse(BehaviorPacing.reserve("t", 3, 10_000), "4th reservation inside window must fail")

            clock.now = 10_001
            assertTrue(BehaviorPacing.reserve("t", 3, 10_000), "slot must free after the window slides")
        } finally {
            BehaviorPacing.clock = BehaviorPacing.Clock.SYSTEM
        }
    }

    @Test
    fun `buckets are independent`() {
        BehaviorPacing.clock = FakeClock(0)
        try {
            BehaviorPacing.resetForTest()
            assertTrue(BehaviorPacing.reserve("a", 1, 10_000))
            assertTrue(BehaviorPacing.reserve("b", 1, 10_000), "separate bucket must not share quota")
        } finally {
            BehaviorPacing.clock = BehaviorPacing.Clock.SYSTEM
        }
    }

    @Test
    fun `used reflects current window occupancy`() {
        val clock = FakeClock(0)
        BehaviorPacing.clock = clock
        try {
            BehaviorPacing.resetForTest()
            repeat(2) { BehaviorPacing.reserve("u", 5, 10_000) }
            assertEquals(2, BehaviorPacing.used("u", 10_000))
            clock.now = 20_000
            assertEquals(0, BehaviorPacing.used("u", 10_000))
        } finally {
            BehaviorPacing.clock = BehaviorPacing.Clock.SYSTEM
        }
    }

    @Test
    fun `daily quota blocks after the limit and rolls over at midnight`() {
        val clock = FakeClock(0)
        BehaviorPacing.clock = clock
        try {
            BehaviorPacing.resetForTest()
            repeat(3) { assertTrue(BehaviorPacing.reserveDaily("d", 3)) }
            assertFalse(BehaviorPacing.reserveDaily("d", 3), "4th same-day reservation must fail")
            assertEquals(0, BehaviorPacing.dailyRemaining("d", 3))

            // Advance one full calendar day: a fresh bucket key must apply.
            clock.now = 24L * 60 * 60 * 1000 + 1
            assertEquals(3, BehaviorPacing.dailyRemaining("d", 3))
            assertTrue(BehaviorPacing.reserveDaily("d", 3))
        } finally {
            BehaviorPacing.clock = BehaviorPacing.Clock.SYSTEM
        }
    }

    @Test
    fun `dailyRemaining never goes negative`() {
        val clock = FakeClock(0)
        BehaviorPacing.clock = clock
        try {
            BehaviorPacing.resetForTest()
            repeat(5) { BehaviorPacing.reserveDaily("n", 2) }
            assertEquals(0, BehaviorPacing.dailyRemaining("n", 2))
        } finally {
            BehaviorPacing.clock = BehaviorPacing.Clock.SYSTEM
        }
    }
}
