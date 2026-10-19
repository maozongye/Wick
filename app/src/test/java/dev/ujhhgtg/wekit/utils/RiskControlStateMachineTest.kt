package dev.ujhhgtg.wekit.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RiskControlStateMachineTest {

    private class FakeClock(var now: Long) {
        fun machine() = RiskControlStateMachine { now }
    }

    @Test
    fun `first hint escalates NONE to L1`() {
        val m = FakeClock(1_000L).machine()
        assertEquals(RiskControlLevel.L1_OBSERVED, m.record(RiskControlSignal.RISK_HINT))
        assertTrue(m.automationAllowed, "L1 must not block automation")
    }

    @Test
    fun `second hint within 24h escalates to L2`() {
        val clock = FakeClock(1_000L)
        val m = clock.machine()
        m.record(RiskControlSignal.RISK_HINT)
        clock.now += 60_000
        assertEquals(RiskControlLevel.L2_THROTTLED, m.record(RiskControlSignal.RISK_HINT))
        assertFalse(m.automationAllowed)
    }

    @Test
    fun `hint outside the window starts a fresh L1`() {
        val clock = FakeClock(1_000L)
        val m = clock.machine()
        m.record(RiskControlSignal.RISK_HINT)
        clock.now += RiskControlStateMachine.L1_WINDOW_MILLIS + 1
        assertEquals(RiskControlLevel.L1_OBSERVED, m.record(RiskControlSignal.RISK_HINT))
        assertTrue(m.automationAllowed)
    }

    @Test
    fun `rate limited jumps straight to L2`() {
        val m = FakeClock(1_000L).machine()
        assertEquals(RiskControlLevel.L2_THROTTLED, m.record(RiskControlSignal.RATE_LIMITED))
        assertFalse(m.automationAllowed)
    }

    @Test
    fun `account restricted jumps straight to L3`() {
        val m = FakeClock(1_000L).machine()
        assertEquals(RiskControlLevel.L3_COOLDOWN, m.record(RiskControlSignal.ACCOUNT_RESTRICTED))
        assertFalse(m.automationAllowed)
    }

    @Test
    fun `L2 cooldown expires into L1 not NONE`() {
        val clock = FakeClock(1_000L)
        val m = clock.machine()
        m.record(RiskControlSignal.RATE_LIMITED)
        clock.now += RiskControlStateMachine.L2_COOLDOWN_MILLIS + 1
        assertEquals(RiskControlLevel.L1_OBSERVED, m.effectiveLevel)
        assertTrue(m.automationAllowed)
        assertEquals(RiskControlLevel.L2_THROTTLED, m.state.level, "persisted level stays L2 until the next record")
    }

    @Test
    fun `L3 cooldown lasts seven days`() {
        val clock = FakeClock(1_000L)
        val m = clock.machine()
        m.record(RiskControlSignal.ACCOUNT_RESTRICTED)
        clock.now += RiskControlStateMachine.L3_COOLDOWN_MILLIS - 1
        assertEquals(RiskControlLevel.L3_COOLDOWN, m.effectiveLevel)
        clock.now += 2
        assertEquals(RiskControlLevel.L1_OBSERVED, m.effectiveLevel)
    }

    @Test
    fun `repeat rate limit during L3 never shortens the cooldown`() {
        val clock = FakeClock(1_000L)
        val m = clock.machine()
        m.record(RiskControlSignal.ACCOUNT_RESTRICTED)
        val deadline = m.state.cooldownUntil
        clock.now += 60_000
        m.record(RiskControlSignal.RATE_LIMITED)
        assertEquals(deadline, m.state.cooldownUntil, "a weaker signal must not reset the L3 deadline")
        assertEquals(RiskControlLevel.L3_COOLDOWN, m.effectiveLevel)
    }

    @Test
    fun `hint during L2 does not downgrade`() {
        val clock = FakeClock(1_000L)
        val m = clock.machine()
        m.record(RiskControlSignal.RATE_LIMITED)
        clock.now += 60_000
        m.record(RiskControlSignal.RISK_HINT)
        assertEquals(RiskControlLevel.L2_THROTTLED, m.effectiveLevel)
    }

    @Test
    fun `escalation from L2 to L3 resets the deadline`() {
        val clock = FakeClock(1_000L)
        val m = clock.machine()
        m.record(RiskControlSignal.RATE_LIMITED)
        val l2Deadline = m.state.cooldownUntil
        clock.now += 60_000
        m.record(RiskControlSignal.ACCOUNT_RESTRICTED)
        assertTrue(m.state.cooldownUntil > l2Deadline, "L3 must extend the cooldown beyond the L2 deadline")
    }
}
