package dev.ujhhgtg.wekit.utils

import kotlinx.coroutines.delay
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Humanised pacing for every batch/automation action that WeChat's server can observe.
 *
 * Fixed-millisecond intervals are themselves a bot signature: any server-side sequence model
 * sees a perfectly periodic stream. All batch features therefore go through this object so
 * their inter-action gaps are log-normally distributed (long right tail, occasional short
 * gaps) and bounded by sliding-window quotas on top.
 *
 * Clock and randomness are injectable for unit tests; production uses wall clock.
 */
object BehaviorPacing {

    /** Pausable clock so tests run without real waiting. */
    fun interface Clock {
        fun now(): Long

        companion object {
            val SYSTEM = Clock(System::currentTimeMillis)
        }
    }

    @Volatile
    var clock: Clock = Clock.SYSTEM

    /** Pauses the caller for a log-normally distributed duration around [medianMs]. */
    suspend fun pause(medianMs: Long, sigma: Double = DEFAULT_SIGMA) {
        delay(nextDelayMs(medianMs, sigma))
    }

    /**
     * Draws one log-normal gap clamped to `[median/3, median*3]`. A uniform `nextLong` must not
     * be substituted: uniform gaps are as statistically recognisable as constant ones.
     */
    fun nextDelayMs(medianMs: Long, sigma: Double = DEFAULT_SIGMA): Long {
        require(medianMs > 0) { "median must be positive" }
        val gaussian = Random.nextDouble() * 2.0 - 1.0 + (Random.nextDouble() * 2.0 - 1.0) +
            (Random.nextDouble() * 2.0 - 1.0) + (Random.nextDouble() * 2.0 - 1.0) +
            (Random.nextDouble() * 2.0 - 1.0) + (Random.nextDouble() * 2.0 - 1.0)
        // Sum of six uniforms is the classic Irwin–Hall approximation of a standard normal.
        val drawn = exp(ln(medianMs.toDouble()) + sigma * gaussian)
        val clamped = drawn.coerceIn(medianMs / 3.0, medianMs * 3.0)
        return clamped.toLong().coerceAtLeast(1L)
    }

    /**
     * Tries to consume one slot from the sliding window [limit]/[windowMillis] for [bucket].
     * Returns false when the window is full; the caller must abort (not queue) in that case.
     */
    fun reserve(bucket: String, limit: Int, windowMillis: Long): Boolean {
        require(limit > 0 && windowMillis > 0)
        val now = clock.now()
        val window = windows.getOrPut(bucket) { ArrayDeque() }
        synchronized(window) {
            while (window.isNotEmpty() && now - window.first() >= windowMillis) {
                window.removeFirst()
            }
            if (window.size >= limit) return false
            window.addLast(now)
        }
        // Opportunistic cleanup keeps the map bounded when features are removed at runtime.
        if (windows.size > 64) windows.entries.removeIf { synchronized(it.value) { it.value.isEmpty() } }
        return true
    }

    /** Number of slots already consumed in the current window (for UI feedback). */
    fun used(bucket: String, windowMillis: Long): Int {
        val now = clock.now()
        val window = windows[bucket] ?: return 0
        synchronized(window) {
            while (window.isNotEmpty() && now - window.first() >= windowMillis) {
                window.removeFirst()
            }
            return window.size
        }
    }

    /**
     * Consumes one slot from a per-calendar-day quota for [bucket]. Daily totals are the hard
     * ceiling above the sliding-window limit: even a patient attacker pattern must stop here.
     */
    fun reserveDaily(bucket: String, limit: Int): Boolean {
        require(limit > 0)
        val day = clock.now() / DAY_MILLIS
        val key = "$bucket@$day"
        return reserve(key, limit, DAY_MILLIS + 1)
    }

    /** Like [reserveDaily] but without consuming; for pre-flight UI checks. */
    fun dailyRemaining(bucket: String, limit: Int): Int {
        val day = clock.now() / DAY_MILLIS
        val key = "$bucket@$day"
        return (limit - used(key, DAY_MILLIS + 1)).coerceAtLeast(0)
    }

    private val windows = ConcurrentHashMap<String, ArrayDeque<Long>>()

    /** Discards all window state. Test-only: quota windows otherwise persist for the process. */
    fun resetForTest() {
        windows.clear()
    }

    const val DEFAULT_SIGMA = 0.4
    private const val DAY_MILLIS = 24L * 60 * 60 * 1000
}
