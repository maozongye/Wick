package dev.ujhhgtg.wekit.loader.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import dev.ujhhgtg.reflekt.reflekt
import dev.ujhhgtg.wekit.loader.utils.ActivityProxy.ActProxyMgr
import dev.ujhhgtg.wekit.loader.utils.ActivityProxy.IntentTokenCache
import dev.ujhhgtg.wekit.utils.HookHandle
import dev.ujhhgtg.wekit.utils.HookParam
import dev.ujhhgtg.wekit.utils.WeLogger
import dev.ujhhgtg.wekit.utils.hookBeforeDirectly
import dev.ujhhgtg.wekit.utils.reflection.ClassLoaders

/**
 * Intercepts activity launch through method hooks instead of the old
 * `ActivityThread.mInstrumentation` field replacement.
 *
 * The field replacement was detectable with a single
 * `getClass() != Instrumentation.class` comparison. Hooking the two methods that actually need
 * interception leaves that field holding the framework's own object, so the comparison has
 * nothing to find.
 *
 * Only these two are intercepted; every other Instrumentation method the old proxy forwarded
 * now runs natively:
 *  - `newActivity` recovers the real intent hidden behind a host-stub launch and instantiates
 *    module activities through the module class loader.
 *  - `callActivityOnCreate` injects module resources and points module activities at the hybrid
 *    class loader (that write targets the activity instance, never a system field).
 */
object InstrumentationHooks {

    private const val TAG = "InstrumentationHooks"

    private val handles = mutableListOf<HookHandle>()

    /**
     * Installs both hooks on the concrete class of the live instrumentation instance.
     *
     * @return true when both installed. The caller falls back to the legacy field replacement
     *   otherwise, so a framework difference degrades instead of breaking module activity
     *   startup outright.
     */
    fun install(instrumentation: Instrumentation): Boolean {
        if (handles.isNotEmpty()) return true

        val target = instrumentation.javaClass
        val installed = runCatching {
            target.reflekt().firstMethodOrNull {
                name = "newActivity"
                parameterCount = 3
                returnType = Activity::class
            }?.hookBeforeDirectly { interceptNewActivity() }?.let(handles::add)

            target.reflekt().firstMethodOrNull {
                name = "callActivityOnCreate"
                parameterCount = 2
            }?.hookBeforeDirectly { interceptCallActivityOnCreate() }?.let(handles::add)

            handles.size == 2
        }.getOrElse {
            WeLogger.e(TAG, "failed to install instrumentation hooks", it)
            false
        }

        if (installed) {
            WeLogger.i(TAG, "instrumentation hooks installed on ${target.name}")
        } else {
            handles.forEach { it.unhook() }
            handles.clear()
            WeLogger.w(TAG, "instrumentation hooks unavailable on ${target.name}")
        }
        return installed
    }

    /** Mirrors the old `ProxyInstrumentation.newActivity`. */
    private fun HookParam.interceptNewActivity() {
        val className = args[1] as String
        val intent = args[2] as Intent

        var resolvedIntent = intent
        var resolvedClass = className

        val recovered = recoverProxyIntent(intent)
        // Diagnostic: a launch of the host stub should always log here. If the stub name shows up
        // with recovered=null, the token was already consumed or never stored.
        WeLogger.d(TAG, "newActivity: class=$className recovered=${recovered?.component?.className}")

        recovered?.takeIf { it.component != null }?.let { real ->
            resolvedIntent = real
            resolvedClass = real.component!!.className
            WeLogger.w(TAG, "recovered intent in newActivity hook: $resolvedClass")
        }

        if (ActProxyMgr.isModuleProxyActivity(resolvedClass)) {
            result = ClassLoaders.MODULE.loadClass(resolvedClass)
                .getDeclaredConstructor().newInstance() as Activity
            return
        }

        // Host activity: hand the recovered intent back to the original method.
        args[1] = resolvedClass
        args[2] = resolvedIntent
    }

    /** Mirrors the old `ProxyInstrumentation.callActivityOnCreate`. */
    private fun HookParam.interceptCallActivityOnCreate() {
        val activity = args[0] as Activity
        ResourcesInjector.injectModuleRes(activity.resources)
        if (!ActProxyMgr.isModuleProxyActivity(activity.javaClass.name)) return

        val cl = ParcelableFixer.hybridClassLoader
        @SuppressLint("PrivateApi")
        Activity::class.java.getDeclaredField("mClassLoader")
            .also { it.isAccessible = true }
            .set(activity, cl)
        activity.intent?.let { intent ->
            intent.setExtrasClassLoader(cl)
            intent.extras?.classLoader = cl
        }
    }

    /** The same recovery contract the old private `tryRecoverIntent` provided. */
    private fun recoverProxyIntent(intent: Intent): Intent? {
        if (!intent.hasExtra(ActProxyMgr.ACTIVITY_PROXY_INTENT_TOKEN)) return null
        val cl = ParcelableFixer.hybridClassLoader
        intent.setExtrasClassLoader(cl)
        val token = intent.getStringExtra(ActProxyMgr.ACTIVITY_PROXY_INTENT_TOKEN)
        return IntentTokenCache.getAndRemove(token)?.also { real ->
            real.setExtrasClassLoader(cl)
            real.extras?.classLoader = cl
        }
    }
}
