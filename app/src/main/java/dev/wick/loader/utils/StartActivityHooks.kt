package dev.wick.loader.utils

import android.app.Instrumentation
import android.content.Intent
import dev.ujhhgtg.reflekt.reflekt
import dev.wick.loader.utils.ActivityProxy.ActProxyMgr
import dev.wick.utils.HookHandle
import dev.wick.utils.HookParam
import dev.wick.utils.WeLogger
import dev.wick.utils.hookBeforeDirectly

/**
 * Redirects module activity launches by hooking `Instrumentation.execStartActivity`
 * instead of replacing the `IActivityManager` / `IActivityTaskManager` singleton proxies.
 *
 * `execStartActivity` is the single convergence point every `startActivity` call passes through
 * before crossing into system_server, so one hook covers the same call set the old binder proxies
 * did. Nothing is written into `Singleton.mInstance`, so enumerating binder proxies no longer
 * reveals the module.
 *
 * `Instrumentation` declares several `execStartActivity` overloads (7 arguments on older
 * releases, 8 once a `reason` string was added) and each `Context`-based entry point is free to
 * call any of them. Every overload carrying an `Intent` must therefore be hooked — matching only
 * the first one installs a hook that reports success and then never fires.
 */
object StartActivityHooks {

    private const val TAG = "StartActivityHooks"

    private val handles = mutableListOf<HookHandle>()

    fun install(instrumentation: Instrumentation): Boolean {
        if (handles.isNotEmpty()) return true

        val installed = runCatching {
            val candidates = instrumentation.javaClass.reflekt().methods {
                name = "execStartActivity"
            }.filter { method -> method.parameterTypes.any { it == Intent::class.java } }

            candidates.forEach { candidate ->
                candidate.hookBeforeDirectly { interceptExecStartActivity() }?.let(handles::add)
            }

            WeLogger.i(
                TAG,
                "execStartActivity overloads hooked: ${handles.size}/${candidates.size}",
            )
            handles.isNotEmpty()
        }.getOrElse {
            WeLogger.e(TAG, "failed to install execStartActivity hooks", it)
            false
        }

        if (!installed) {
            handles.forEach { it.unhook() }
            handles.clear()
            WeLogger.w(TAG, "no execStartActivity overload could be hooked")
        }
        return installed
    }

    private fun HookParam.interceptExecStartActivity() {
        var hit = false
        args.forEachIndexed { index, arg ->
            when (arg) {
                is Intent -> if (ActProxyMgr.shouldProxy(arg)) {
                    args[index] = ActProxyMgr.createTokenWrapper(arg)
                    hit = true
                }
                is Array<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    val intents = arg as? Array<Intent?> ?: return@forEachIndexed
                    intents.forEachIndexed { i, intent ->
                        if (intent != null && ActProxyMgr.shouldProxy(intent)) {
                            intents[i] = ActProxyMgr.createTokenWrapper(intent)
                            hit = true
                        }
                    }
                }
            }
        }
    }
}
