@file:Suppress("NOTHING_TO_INLINE")

package dev.ujhhgtg.wekit.features.core

import android.content.Context
import androidx.annotation.StringRes
import dev.ujhhgtg.reflekt.reflected.BaseReflectedMethod
import dev.ujhhgtg.reflekt.reflected.ReflectedConstructor
import dev.ujhhgtg.reflekt.reflekt
import dev.ujhhgtg.wekit.dexkit.dsl.BaseDexDelegate
import dev.ujhhgtg.wekit.dexkit.resolution.DexResolutionStatus
import dev.ujhhgtg.wekit.dexkit.cache.DexCacheManager
import dev.ujhhgtg.wekit.dexkit.abc.IResolveDex
import dev.ujhhgtg.wekit.dexkit.dsl.DexConstructorDelegate
import dev.ujhhgtg.wekit.dexkit.dsl.DexMethodDelegate
import dev.ujhhgtg.wekit.utils.HookAction
import dev.ujhhgtg.wekit.utils.HookHandle
import dev.ujhhgtg.wekit.utils.HookParam
import dev.ujhhgtg.wekit.utils.TargetProcess
import dev.ujhhgtg.wekit.utils.TargetProcesses
import dev.ujhhgtg.wekit.utils.RiskControlGate
import dev.ujhhgtg.wekit.features.items.system.agent.WeChatForegroundTracker
import dev.ujhhgtg.wekit.utils.WeLogger
import dev.ujhhgtg.wekit.utils.hookAfterDirectly
import dev.ujhhgtg.wekit.utils.hookBeforeDirectly
import org.luckypray.dexkit.DexKitBridge
import java.lang.reflect.Executable
import kotlin.reflect.KClass

abstract class BaseFeature {

    abstract val technicalId: String

    @get:StringRes
    abstract val nameRes: Int

    abstract val categoryIds: List<String>

    /** Processes where this feature may be enabled. Defaults to the main process only. */
    open val targetProcesses: Set<TargetProcess> = setOf(TargetProcess.MAIN)

    @get:StringRes
    open val descriptionRes: Int? = null

    val technicalPath: String
        get() = categoryIds.joinToString(",") + "/" + technicalId

    fun localizedName(context: Context): String = context.getString(nameRes)

    fun localizedDescription(context: Context): String =
        descriptionRes?.let(context::getString).orEmpty()

    open fun startup() {
        error("You shouldn't inherit BaseFeature")
    }

    /** Whether this feature's hooks are currently installed (runtime truth). */
    var isActive: Boolean = false
        protected set

    /**
     * True for features that produce server-visible automation (batch sends, auto-likes,
     * …). [enable] refuses to run them while [RiskControlGate] is at L2 or above, so the module
     * stops all automation the moment WeChat signals the account is under scrutiny.
     */
    open val automationRisk: Boolean get() = false

    /**
     * When non-null, this feature uninstalls its hooks after this many milliseconds without
     * interaction and reinstalls them on the next one.
     *
     * The host can only inspect hook state while it is running code, so shrinking the window in
     * which hooks exist shrinks what any inspection can find. Defaults to null: leaving hooks
     * installed for the whole process is the safe behaviour, and only features whose hooks are
     * both expensive to observe and cheap to reinstall opt in.
     *
     * Idle means "no interaction for this long"; see [touch]. Hooks are also removed while the
     * host is in the background, so the window tracks actual use rather than process lifetime.
     */
    open val hookIdleTimeoutMs: Long? = null

    private var idleDeadline = 0L

    private var idleScheduled = false

    /**
     * Marks this feature as interacted with, postponing its idle deadline and reinstalling
     * hooks if an earlier idle sweep removed them.
     *
     * Call this from any handler that means the user is actively using the feature (a settings
     * row opened, the owning UI shown, the feature's own API invoked). Features with a null
     * [hookIdleTimeoutMs] ignore it.
     */
    fun touch() {
        val timeout = hookIdleTimeoutMs ?: return
        idleDeadline = System.currentTimeMillis() + timeout
        // An idle sweep may have unhooked this feature; interaction is the signal to restore it.
        // enable() is a no-op when already active, so repeated touches cost nothing.
        if (!isActive) enable()
    }

    /** Installs the idle timer once, on the first successful enable. */
    private fun scheduleIdleCheck() {
        if (idleScheduled) return
        idleScheduled = true
        HookIdleScheduler.watch(this)
    }

    /**
     * True when this feature should currently keep its hooks installed. Read by
     * [HookIdleScheduler]; a feature with no timeout always returns true.
     *
     * Backgrounding the host closes the window immediately: hooks that only matter while the
     * user is in the app should not stay installed behind the launcher.
     */
    fun hasLiveInteraction(): Boolean {
        if (hookIdleTimeoutMs == null) return true
        if (!WeChatForegroundTracker.isForeground) return false
        return System.currentTimeMillis() < idleDeadline
    }

    fun enable() {
        if (TargetProcesses.currentType !in targetProcesses || isActive) return
        if (automationRisk && !RiskControlGate.automationAllowed) {
            WeLogger.w(
                TAG,
                "automation blocked by risk-control gate: $technicalPath (level=${RiskControlGate.level})",
            )
            return
        }
        // Features that were disabled at startup are not resolved eagerly (see FeaturesLoader).
        // The first time the user turns one on, load its descriptors here so hooks can install;
        // without this the feature would silently never work.
        ensureDexResolved()

        runCatching {
            isActive = true
            onEnable()
            touch()
            if (hookIdleTimeoutMs != null) scheduleIdleCheck()
        }.onFailure { e ->
            WeLogger.e(TAG, "failed to enable feature $technicalPath", e)
            // ensure transaction is fully discarded
            unhookAll()
            isActive = false
        }
    }

    fun disable() {
        if (!isActive) return

        runCatching {
            isActive = false
            unhookAll()
            onDisable()
        }.onFailure { e ->
            WeLogger.e(TAG, "failed to disable feature $technicalPath", e)
            isActive = true
        }
    }

    open fun onEnable() {}

    open fun onDisable() {}

    /**
     * Loads this feature's Dex descriptors from cache if it has never been resolved.
     *
     * Startup only resolves features that will actually run, so a feature enabled later needs
     * its descriptors before [onEnable] touches them. A cache miss is logged loudly: the
     * feature then fails inside its own `runCatching` instead of appearing to work.
     */
    private fun ensureDexResolved() {
        if (this !is IResolveDex || dexDelegates.isEmpty()) return
        if (dexDelegates.none { it.diagnostic.status == DexResolutionStatus.PENDING }) return
        val cache = runCatching { DexCacheManager.loadItemCache(this) }.getOrNull()
        if (cache == null) {
            WeLogger.w(
                TAG,
                "no Dex cache for $technicalPath; it was never resolved, so its hooks cannot install",
            )
            return
        }
        val missing = loadFromCache(cache)
        if (missing.isNotEmpty()) {
            WeLogger.w(TAG, "$technicalPath missing $missing descriptor(s) from cache")
        }
    }

    private val _dexDelegates = mutableListOf<BaseDexDelegate>()
    val dexDelegates: List<BaseDexDelegate> get() = _dexDelegates
    fun registerDexDelegate(d: BaseDexDelegate) {
        d.owner = this
        _dexDelegates += d
    }

    fun resolveInlineDex(dexKit: DexKitBridge) {
        dexDelegates.forEach { it.findInline(dexKit) }
    }

    val unhooks = mutableListOf<HookHandle>()
    fun registerUnhook(u: HookHandle) {
        unhooks += u
    }

    fun unhookAll() {
        unhooks.forEach { it.unhook() }
        unhooks.clear()
    }

    // --- hookBefore ---

    fun Executable.hookBefore(
        priority: Int = 50,
        action: HookAction
    ) = registerUnhook(
        hookBeforeDirectly(priority) {
            executeHookAction(this, action)
        }
    )

    @JvmName("hookBefore2")
    fun BaseReflectedMethod.hookBefore(
        priority: Int = 50,
        action: HookAction
    ) = self.hookBefore(priority, action)

    @JvmName("hookBefore3")
    fun ReflectedConstructor<*>.hookBefore(
        priority: Int = 50,
        action: HookAction
    ) = this.self.hookBefore(priority, action)

    fun Class<*>.hookBeforeOnCreate(
        action: HookAction
    ) = this.reflekt().firstMethod { name = "onCreate" }.hookBefore(50, action)

    fun Class<*>.hookAfterOnCreate(
        action: HookAction
    ) = this.reflekt().firstMethod { name = "onCreate" }.hookAfter(50, action)

    fun KClass<*>.hookBeforeOnCreate(
        action: HookAction
    ) = this.reflekt().firstMethod { name = "onCreate" }.hookBefore(50, action)

    fun KClass<*>.hookAfterOnCreate(
        action: HookAction
    ) = this.reflekt().firstMethod { name = "onCreate" }.hookAfter(50, action)

    // --- end hookBefore ---

    // --- hookAfter ---

    fun Executable.hookAfter(
        priority: Int = 50,
        action: HookAction
    ) = registerUnhook(
        hookAfterDirectly(priority) {
            executeHookAction(this, action)
        }
    )

    @JvmName("hookAfter2")
    fun BaseReflectedMethod.hookAfter(
        priority: Int = 50,
        action: HookAction
    ) = self.hookAfter(priority, action)

    @JvmName("hookAfter3")
    fun ReflectedConstructor<*>.hookAfter(
        priority: Int = 50,
        action: HookAction
    ) = this.self.hookAfter(priority, action)

    // --- end hookAfter ---

    // --- dex delegate ---

    fun DexMethodDelegate.hookBefore(
        priority: Int = 50,
        action: HookAction
    ) = method.hookBefore(priority, action)

    fun DexMethodDelegate.hookAfter(
        priority: Int = 50,
        action: HookAction
    ) = method.hookAfter(priority, action)

    fun DexConstructorDelegate.hookBefore(
        priority: Int = 50,
        action: HookAction
    ) = constructor.hookBefore(priority, action)

    fun DexConstructorDelegate.hookAfter(
        priority: Int = 50,
        action: HookAction
    ) = constructor.hookAfter(priority, action)

    // --- end dex delegate ---

    fun executeHookAction(param: HookParam, action: HookAction) {
        runCatching {
            action(param)
        }.onFailure { e -> WeLogger.e("executeHookAction", "failed to execute hook of $technicalId", e) }
    }

    companion object {
        private const val TAG = "BaseFeature"
    }
}
