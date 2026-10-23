package dev.wick.features.core

import android.os.Handler
import android.os.Looper
import dev.wick.features.items.system.agent.WeChatForegroundTracker
import dev.wick.utils.WeLogger

/**
 * Reinstalls and removes hooks as a feature's interaction window opens and closes.
 *
 * Kept separate from [BaseFeature] so the scheduling policy (one main-thread tick for every
 * opted-in feature, rather than a timer each) is visible in one place and does not add a
 * Handler field to every feature object.
 *
 * Only features whose [BaseFeature.hookIdleTimeoutMs] is non-null are watched. A watched
 * feature that goes idle is [BaseFeature.disable]d; the next [BaseFeature.touch] makes the
 * tick re-enable it. Because `enable` and `disable` are the existing public entry points,
 * reinstallation follows the same path a user toggle would take.
 */
object HookIdleScheduler {

    private const val TAG = "HookIdleScheduler"

    /** Interval between idle checks. Coarse on purpose: this is not a latency-sensitive path. */
    private const val TICK_MS = 5_000L

    private val watched = mutableListOf<BaseFeature>()

    private var running = false

    private val handler = Handler(Looper.getMainLooper())

    private val tick = object : Runnable {
        override fun run() {
            sweep()
            handler.postDelayed(this, TICK_MS)
        }
    }

    fun watch(feature: BaseFeature) {
        synchronized(watched) {
            if (watched.none { it === feature }) watched += feature
        }
        if (running) return
        running = true
        // The window closes on backgrounding, so the tracker must be live before the first tick.
        WeChatForegroundTracker.ensureRegistered()
        handler.postDelayed(tick, TICK_MS)
    }

    /**
     * Disables every watched feature whose interaction window has closed.
     *
     * `disable()` is idempotent and already guarded by `isActive`, so sweeping an already-idle
     * feature is a no-op. Re-enabling is left to the feature's own interaction point: it calls
     * [BaseFeature.touch], which reopens the window and lets the next enable succeed.
     */
    private fun sweep() {
        val snapshot = synchronized(watched) { watched.toList() }
        for (feature in snapshot) {
            if (!feature.isActive) continue
            if (feature.hasLiveInteraction()) continue
            WeLogger.d(TAG, "idle timeout: unhooking ${feature.technicalPath}")
            feature.disable()
        }
    }
}
