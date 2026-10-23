package dev.wick.utils

/** Circuit-breaker severity. Escalation only; expiry downgrades at most to L1_OBSERVED. */
enum class RiskControlLevel {
    NONE,
    L1_OBSERVED,
    L2_THROTTLED,
    L3_COOLDOWN,
}

/** What WeChat told us: a hint message, a rate limit, or an account restriction. */
enum class RiskControlSignal {
    /** A risk/security hint was observed in a system message. */
    RISK_HINT,
    /** A NetScene error -34 / 操作过于频繁. */
    RATE_LIMITED,
    /** A NetScene error -24, or an explicit restriction/ban text. */
    ACCOUNT_RESTRICTED,
}

/**
 * Pure risk-escalation state machine behind [RiskControlGate].
 *
 * Kept free of any persistence or Android dependency so the escalation/cooldown/downgrade
 * rules are unit-testable on the desktop JVM. [RiskControlGate] owns persistence and wraps
 * this class; the rules themselves live here, in one place.
 */
class RiskControlStateMachine(
    private val now: () -> Long,
) {

    data class State(
        val level: RiskControlLevel = RiskControlLevel.NONE,
        /** Monotonic timestamp of the first unresolved risk hint. */
        val firstHintAt: Long = 0L,
        /** Risk hints seen since [firstHintAt]. */
        val hintsInWindow: Int = 0,
        /** Cooldown deadline while L2/L3; 0 when none. */
        val cooldownUntil: Long = 0L,
    )

    var state: State = State()
        private set

    /** Replaces the whole state at once — used by the persistence shell on rehydration. */
    fun restore(level: RiskControlLevel, firstHintAt: Long, hintsInWindow: Int, cooldownUntil: Long) {
        state = State(level, firstHintAt, hintsInWindow, cooldownUntil)
    }

    /** The externally visible level: an expired cooldown downgrades to L1_OBSERVED. */
    val effectiveLevel: RiskControlLevel
        get() {
            val stored = state.level
            if (stored == RiskControlLevel.L2_THROTTLED || stored == RiskControlLevel.L3_COOLDOWN) {
                if (now() >= state.cooldownUntil) return RiskControlLevel.L1_OBSERVED
            }
            return stored
        }

    val automationAllowed: Boolean
        get() = effectiveLevel < RiskControlLevel.L2_THROTTLED

    /** Records one observation and returns the resulting effective level. */
    fun record(signal: RiskControlSignal): RiskControlLevel {
        val now = now()
        state = when (signal) {
            RiskControlSignal.ACCOUNT_RESTRICTED -> state.copy(
                level = RiskControlLevel.L3_COOLDOWN,
                cooldownUntil = now + L3_COOLDOWN_MILLIS,
            )

            RiskControlSignal.RATE_LIMITED -> escalateTo(RiskControlLevel.L2_THROTTLED, now + L2_COOLDOWN_MILLIS)

            RiskControlSignal.RISK_HINT -> when (effectiveLevel) {
                RiskControlLevel.L3_COOLDOWN, RiskControlLevel.L2_THROTTLED -> state
                RiskControlLevel.L1_OBSERVED -> {
                    val fresh = state.firstHintAt > 0 && now - state.firstHintAt < L1_WINDOW_MILLIS
                    if (fresh && state.hintsInWindow + 1 >= 2) {
                        escalateTo(RiskControlLevel.L2_THROTTLED, now + L2_COOLDOWN_MILLIS)
                    } else {
                        state.copy(hintsInWindow = state.hintsInWindow + 1)
                    }
                }
                RiskControlLevel.NONE -> State(
                    level = RiskControlLevel.L1_OBSERVED,
                    firstHintAt = now,
                    hintsInWindow = 1,
                )
            }
        }
        return effectiveLevel
    }

    /**
     * Escalation never shortens an active cooldown and never lowers the level: a repeated -34
     * during an L3 window must not downgrade the restriction to L2 (the server has already
     * told us the account is restricted; a weaker later signal does not retract that).
     */
    private fun escalateTo(level: RiskControlLevel, cooldownDeadline: Long): State {
        val stronger = level.ordinal > state.level.ordinal
        val deadline = if (stronger) cooldownDeadline else maxOf(state.cooldownUntil, cooldownDeadline)
        // Only raise the level; a weaker signal still extends the deadline if it would outlast it.
        val effectiveLevel = if (stronger) level else state.level
        return state.copy(level = effectiveLevel, cooldownUntil = deadline)
    }

    companion object {
        const val L1_WINDOW_MILLIS = 24L * 60 * 60 * 1000
        const val L2_COOLDOWN_MILLIS = 24L * 60 * 60 * 1000
        const val L3_COOLDOWN_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}
