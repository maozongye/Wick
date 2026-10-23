package dev.wick.features.core

import dev.wick.utils.TargetProcesses
import dev.wick.utils.WeLogger

/**
 * An [ApiFeature] whose hooks are installed only once something actually consumes the API.
 *
 * Plain [ApiFeature] installs its hooks at startup for every WeChat launch, even when no
 * consumer ever calls into it. Each of those hooks is a permanent, inspectable modification
 * of a host method — the cheapest signal a client-side detector has. A lazy API registers its
 * consumer set eagerly (cheap, in-process bookkeeping) and installs the hooks on the first
 * registration.
 *
 * Subclasses implement [installHooks] instead of [onEnable]; it runs at most once per process
 * and only when [arm] is called.
 *
 * Note: this is intentionally NOT applied to APIs that the settings entry itself depends on
 * (`WeSettingsInjector`) or to UI-host plumbing (`WeViewTreeLifecycleProvider`) — those must
 * exist before any consumer can possibly register.
 */
abstract class LazyFeature : ApiFeature() {

    @Volatile
    private var armed = false

    /** True once [installHooks] has run. */
    protected val isArmed: Boolean get() = armed

    /**
     * Installs the real hooks. Called at most once, on whichever thread first arms this API.
     * Must be idempotent with respect to being called only once, not to being called twice.
     */
    protected abstract fun installHooks()

    /**
     * Arms this API: installs hooks on first call, a no-op afterwards. Call this from every
     * public entry point that needs the hooks to be live (a provider/listener registration,
     * or the first invocation of an exposed operation).
     */
    protected fun arm() {
        if (armed) return
        synchronized(this) {
            if (armed) return
            if (TargetProcesses.currentType !in targetProcesses) {
                WeLogger.w(TAG, "skipping lazy hook installation outside target process: $technicalPath")
                return
            }
            armed = true
            hookHandles()
        }
    }

    private fun hookHandles() {
        // BaseFeature.enable() would short-circuit on an already-active feature; instead the
        // handles registered by installHooks() are adopted directly into this feature's list
        // so disable()/unhookAll() keep working exactly as for an eagerly enabled feature.
        isActive = true
        runCatching { installHooks() }.onFailure { error ->
            WeLogger.e(TAG, "failed to install lazy hooks for $technicalPath", error)
            unhookAll()
            isActive = false
            armed = false
        }
    }

    final override fun onEnable() {
        // Hooks are installed by arm(), never by startup.
    }

    override fun onDisable() {
        unhookAll()
        armed = false
    }

    private companion object {
        const val TAG = "LazyFeature"
    }
}
