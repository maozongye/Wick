package dev.ujhhgtg.wekit.loader.utils

import android.app.Instrumentation
import android.content.ComponentName
import android.content.Intent
import dev.ujhhgtg.reflekt.reflekt
import dev.ujhhgtg.wekit.loader.utils.ActivityProxy.ActProxyMgr
import dev.ujhhgtg.wekit.loader.utils.ActivityProxy.IntentTokenCache
import dev.ujhhgtg.wekit.utils.HookHandle
import dev.ujhhgtg.wekit.utils.HookParam
import dev.ujhhgtg.wekit.utils.HostInfo
import dev.ujhhgtg.wekit.utils.WeLogger
import dev.ujhhgtg.wekit.utils.hookBeforeDirectly

/**
 * Redirects module activity launches by hooking `Instrumentation.execStartActivity`
 * instead of replacing the `IActivityManager` / `IActivityTaskManager` singleton proxies.
 *
 * `execStartActivity` is the single convergence point every `startActivity` call passes
 * through before crossing into system_server, so one hook covers the same call set the old
 * binder proxies did. Nothing is written into `Singleton.mInstance`, so enumerating binder
 * proxies no longer reveals the module.
 *
 * Argument positions differ across releases, so intents are located by type rather than index,
 * mirroring what the old invocation handler did.
 */
object StartActivityHooks {

    private const val TAG = "StartActivityHooks"

    private val handles = mutableListOf<HookHandle>()

    fun install(instrumentation: Instrumentation): Boolean {
        if (handles.isNotEmpty()) return true

        val installed = runCatching {
            instrumentation.javaClass.reflekt()
                .firstMethodOrNull {
                    name = "execStartActivity"
                }
                ?.hookBeforeDirectly { interceptExecStartActivity() }
                ?.let(handles::add)

            handles.size == 1
        }.getOrElse {
            WeLogger.e(TAG, "failed to install execStartActivity hook", it)
            false
        }

        if (installed) {
            WeLogger.i(TAG, "execStartActivity hook installed")
        } else {
            handles.forEach { it.unhook() }
            handles.clear()
            WeLogger.w(TAG, "execStartActivity hook unavailable")
        }
        return installed
    }

    private fun HookParam.interceptExecStartActivity() {
        var hit = false
        args.forEachIndexed { index, arg ->
            when (arg) {
                is Intent -> if (shouldProxy(arg)) {
                    args[index] = createTokenWrapper(arg)
                    hit = true
                }
                is Array<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    val intents = arg as? Array<Intent?> ?: return@forEachIndexed
                    intents.forEachIndexed { i, intent ->
                        if (intent != null && shouldProxy(intent)) {
                            intents[i] = createTokenWrapper(intent)
                            hit = true
                        }
                    }
                }
            }
        }
        // Diagnostic: distinguishes "hook never ran" from "ran but matched nothing".
        if (!hit) WeLogger.d(TAG, "execStartActivity observed, ${args.size} args, no module intent")
    }

    private fun shouldProxy(intent: Intent): Boolean =
        intent.component?.let { ActProxyMgr.isModuleProxyActivity(it.className) } == true

    private fun createTokenWrapper(raw: Intent): Intent {
        val token = IntentTokenCache.put(Intent(raw))
        val className = raw.component!!.className
        val proxyClass = if (className.contains("SettingsActivity")) {
            ActProxyMgr.SETTINGS_PROXY
        } else {
            ActProxyMgr.TRANSPARENT_PROXY
        }
        return Intent(raw).apply {
            component = ComponentName(HostInfo.packageName, proxyClass)
            flags = raw.flags
            action = raw.action
            setDataAndType(raw.data, raw.type)
            raw.categories?.forEach { addCategory(it) }
            putExtra(ActProxyMgr.ACTIVITY_PROXY_INTENT_TOKEN, token)
            setExtrasClassLoader(ParcelableFixer.hybridClassLoader)
        }.also {
            WeLogger.i(TAG, "hijacked startActivity via token: $className -> $proxyClass")
        }
    }
}
