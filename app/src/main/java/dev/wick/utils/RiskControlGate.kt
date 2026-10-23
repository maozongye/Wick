package dev.wick.utils

import dev.wick.data.KvStore

/**
 * Risk-control circuit breaker: the single gate every automation feature must pass.
 *
 * WeChat escalates gradually (risk hints → short restriction → longer restriction) before any
 * ban; those signals arrive in the `message` table and in NetScene error codes. This gate turns
 * them into a hard block on automation, so the module stops digging while the account is under
 * active scrutiny.
 *
 * [RiskControlStateMachine] owns the escalation rules; this object only persists its [State]
 * across restarts through [KvStore] and rehydrates it lazily. Levels never auto-clear to
 * NONE: after a cooldown expires the gate stays at L1_OBSERVED, because an account that has
 * drawn attention stays warm in the server-side model far longer than any local cooldown.
 */
object RiskControlGate {

    private const val KEY_LEVEL = "rc_level"
    private const val KEY_FIRST_HINT_AT = "rc_l1_at"
    private const val KEY_HINTS = "rc_l1_count"
    private const val KEY_COOLDOWN_UNTIL = "rc_until"

    private val machine = RiskControlStateMachine(now = { System.currentTimeMillis() })

    @Volatile
    private var hydrated = false

    /** Rehydrates the persisted state into the machine on first use of this gate. */
    private fun hydrated(): RiskControlStateMachine {
        if (!hydrated) {
            synchronized(this) {
                if (!hydrated) {
                    machine.restore(
                        level = RiskControlLevel.entries.getOrNull(KvStore.getIntOrDef(KEY_LEVEL, 0))
                            ?: RiskControlLevel.NONE,
                        firstHintAt = KvStore.getLongOrDef(KEY_FIRST_HINT_AT, 0L),
                        hintsInWindow = KvStore.getIntOrDef(KEY_HINTS, 0),
                        cooldownUntil = KvStore.getLongOrDef(KEY_COOLDOWN_UNTIL, 0L),
                    )
                    hydrated = true
                }
            }
        }
        return machine
    }

    val level: RiskControlLevel
        get() = hydrated().effectiveLevel

    val automationAllowed: Boolean
        get() = hydrated().automationAllowed

    /** Cooldown deadline in millis; 0 when no cooldown is active. */
    val cooldownUntil: Long
        get() = hydrated().state.cooldownUntil

    /** Records one observation and persists the resulting state. */
    fun record(signal: RiskControlSignal): RiskControlLevel = synchronized(this) {
        val result = hydrated().record(signal)
        val state = machine.state
        KvStore.putInt(KEY_LEVEL, state.level.ordinal)
        KvStore.putLong(KEY_FIRST_HINT_AT, state.firstHintAt)
        KvStore.putInt(KEY_HINTS, state.hintsInWindow)
        KvStore.putLong(KEY_COOLDOWN_UNTIL, state.cooldownUntil)
        val previous = previousLevel
        previousLevel = result
        if (result != previous) {
            onLevelChanged?.invoke(result)
        }
        if (result == RiskControlLevel.L3_COOLDOWN && previous != RiskControlLevel.L3_COOLDOWN) {
            onCooldownEnter?.invoke()
        }
        result
    }

    /**
     * Currently visible level as of the last [record] in this process. Used only to detect
     * transitions; the authoritative value is [level] (which also accounts for cooldowns
     * expiring while the process runs).
     */
    @Volatile
    private var previousLevel: RiskControlLevel? = null

    /**
     * Invoked whenever a record escalates to L3. Registered by the UI-side circuit breaker to
     * mirror the restriction into SafeMode; kept as a hook so this object stays dependency-free.
     */
    @Volatile
    var onCooldownEnter: (() -> Unit)? = null

    /**
     * Invoked on every level transition. Consumers use this to revoke already-installed hooks
     * when the account enters L2+: blocking future `enable()` calls is not enough, because the
     * hooks installed before the signal are exactly what a detector would look for.
     */
    @Volatile
    var onLevelChanged: ((RiskControlLevel) -> Unit)? = null

    /** Test-only: forgets the hydrated state and clears persistence. */
    fun resetForTest() {
        synchronized(this) {
            machine.restore(RiskControlLevel.NONE, 0L, 0, 0L)
            hydrated = false
            listOf(KEY_LEVEL, KEY_FIRST_HINT_AT, KEY_HINTS, KEY_COOLDOWN_UNTIL).forEach(KvStore::remove)
        }
    }
}
