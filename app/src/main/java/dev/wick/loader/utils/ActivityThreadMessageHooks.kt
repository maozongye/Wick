package dev.wick.loader.utils

import android.annotation.SuppressLint
import android.app.ActivityThread
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Message
import dev.ujhhgtg.reflekt.reflekt
import dev.ujhhgtg.reflekt.utils.makeAccessible
import dev.wick.loader.utils.ActivityProxy.ActProxyMgr
import dev.wick.loader.utils.ActivityProxy.IntentTokenCache
import dev.wick.utils.HookHandle
import dev.wick.utils.HookParam
import dev.wick.utils.WeLogger
import dev.wick.utils.hookBeforeDirectly

/**
 * Recovers the real intent from the launch message instead of replacing
 * `Handler.mCallback` on ActivityThread's main handler.
 *
 * The old approach installed a `Handler.Callback` into `mH.mCallback`; a host-side integrity
 * check reading that field sees a non-null value where the framework leaves it null.
 *
 * The hook targets `Handler.dispatchMessage`, not `ActivityThread$H.handleMessage`.
 * `dispatchMessage` routes a message three ways, and `handleMessage` is only the last of them:
 *
 *  - `msg.callback != null`            -> `handleCallback(msg)`
 *  - `mCallback.handleMessage(msg)`    -> returns without calling `handleMessage`
 *  - otherwise                         -> `handleMessage(msg)`
 *
 * Hooking `handleMessage` therefore misses every message whenever the host has installed its
 * own `mCallback` — which is exactly the state a module activity launch fails in. Hooking
 * `dispatchMessage` sits above that split and sees the message either way, while still leaving
 * `mCallback` null as the framework set it.
 *
 * Message ids are stable framework constants:
 *  - 100 `LAUNCH_ACTIVITY` (before Android 9)
 *  - 159 `EXECUTE_TRANSACTION` (Android 9+)
 */
object ActivityThreadMessageHooks {

    private const val TAG = "ActivityThreadMessageHooks"

    private const val LAUNCH_ACTIVITY = 100
    private const val EXECUTE_TRANSACTION = 159

    private val handles = mutableListOf<HookHandle>()

    fun install(): Boolean {
        if (handles.isNotEmpty()) return true

        // Primary path: Handler.dispatchMessage sees every message regardless of how the
        // host routes it (mCallback, msg.callback, or plain handleMessage).
        runCatching {
            Class.forName("android.app.ActivityThread\$H")
                .reflekt()
                .firstMethodOrNull {
                    name = "dispatchMessage"
                    parameterCount = 1
                    superclass(true)
                }
                ?.hookBeforeDirectly { interceptMessage() }
                ?.let(handles::add)
        }.onFailure { WeLogger.e(TAG, "dispatchMessage hook failed", it) }

        // Safety net: performLaunchActivity is the single funnel every activity launch passes
        // through, independent of the Handler routing above. Recovering here catches a wrapper
        // that survived the message path (or bypassed it, e.g. msg.callback != null).
        runCatching {
            Class.forName("android.app.ActivityThread")
                .reflekt()
                .firstMethodOrNull {
                    name = "performLaunchActivity"
                    parameterCount = 2
                }
                ?.hookBeforeDirectly { interceptPerformLaunch() }
                ?.let(handles::add)
        }.onFailure { WeLogger.e(TAG, "performLaunchActivity hook failed", it) }

        val installed = handles.isNotEmpty()
        if (installed) {
            WeLogger.i(TAG, "launch recovery installed: ${handles.size} hook(s)")
        } else {
            handles.forEach { it.unhook() }
            handles.clear()
            WeLogger.w(TAG, "no launch recovery hook available")
        }
        return installed
    }

    private fun HookParam.interceptMessage() {
        val msg = args[0] as Message
        // Diagnostic: whether this hook is reached at all, and for which message ids. If no line
        // appears while a module activity is launched, the host installed its own mH.mCallback
        // and Handler.dispatchMessage never falls through to handleMessage.
        if (msg.what == LAUNCH_ACTIVITY || msg.what == EXECUTE_TRANSACTION) {
            WeLogger.d(TAG, "handleMessage reached, what=${msg.what}")
        }
        when (msg.what) {
            LAUNCH_ACTIVITY -> handleLaunchActivity(msg)
            EXECUTE_TRANSACTION -> handleExecuteTransaction(msg)
        }
    }

    /**
     * Safety net: the last point before the framework resolves the activity class. The
     * ActivityClientRecord's intent is rewritten here regardless of how the launch message was
     * routed, and a visible marker lets the user report the failure stage without pulling logs.
     */
    private fun HookParam.interceptPerformLaunch() {
        runCatching {
            val record = args[0] ?: return@runCatching
            val intentField = record.javaClass.getDeclaredField("intent").makeAccessible()
            val wrapper = intentField.get(record) as? Intent
            if (wrapper == null || !wrapper.hasExtra(ActProxyMgr.ACTIVITY_PROXY_INTENT_TOKEN)) {
                return@runCatching
            }
            val recovered = recoverIntent(wrapper)
            if (recovered != null) {
                intentField.set(record, recovered.intent)
                WeLogger.i(TAG, "performLaunch recovery: ${recovered.intent.component?.className}")
            } else {
                // The wrapper carries our token but the mapping is gone. This is the exact
                // signature of a black-screen bounce, so make it visible on screen.
                WeLogger.w(TAG, "launch wrapper unresolved")
            }
        }.onFailure { WeLogger.e(TAG, "performLaunch error", it) }
    }

    private fun recoverIntent(wrapper: Intent?): RecoveredIntent? {
        wrapper ?: return null
        val cl = ParcelableFixer.hybridClassLoader
        wrapper.setExtrasClassLoader(cl)
        if (!wrapper.hasExtra(ActProxyMgr.ACTIVITY_PROXY_INTENT_TOKEN)) return null

        val token = wrapper.getStringExtra(ActProxyMgr.ACTIVITY_PROXY_INTENT_TOKEN) ?: return null
        val real = IntentTokenCache.get(token) ?: run {
            WeLogger.w(TAG, "token expired or lost in handler: $token")
            return null
        }
        real.setExtrasClassLoader(cl)
        real.extras?.classLoader = cl
        return RecoveredIntent(token, real)
    }

    private fun handleLaunchActivity(msg: Message) {
        runCatching {
            val record = msg.obj
            val intentField = record.javaClass.getDeclaredField("intent").makeAccessible()
            val wrapper = intentField.get(record) as? Intent
            recoverIntent(wrapper)?.let { recovered ->
                intentField.set(record, recovered.intent)
            }
        }.onFailure { WeLogger.e(TAG, "handleLaunchActivity error", it) }
    }

    private fun handleExecuteTransaction(msg: Message) {
        runCatching {
            val transaction = msg.obj
            val callbacks = transaction.javaClass.getDeclaredMethod("getCallbacks")
                .makeAccessible()
                .invoke(transaction) as? List<*> ?: return

            callbacks.forEach { item ->
                if (item != null && item.javaClass.name.contains("LaunchActivityItem")) {
                    val intentField = item.javaClass.getDeclaredField("mIntent").makeAccessible()
                    val wrapper = intentField.get(item) as? Intent
                    recoverIntent(wrapper)?.let { recovered ->
                        intentField.set(item, recovered.intent)

                        // Android 12 keeps a second ActivityClientRecord while launching.
                        // Updating only LaunchActivityItem leaves the proxy intent in that
                        // record, so ActivityThread instantiates the proxy activity instead.
                        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.S..Build.VERSION_CODES.S_V2) {
                            updateLaunchingActivityIntent(transaction, recovered.intent)
                        }
                    }
                }
            }
        }.onFailure { WeLogger.e(TAG, "handleExecuteTransaction error", it) }
    }

    @SuppressLint("PrivateApi")
    private fun updateLaunchingActivityIntent(transaction: Any, intent: Intent) {
        val token = transaction.javaClass.getMethod("getActivityToken")
            .invoke(transaction) as IBinder
        val activityThread = ActivityThread.currentActivityThread()
        val record = activityThread.javaClass
            .getMethod("getLaunchingActivity", IBinder::class.java)
            .invoke(activityThread, token) ?: return
        record.javaClass.getDeclaredField("intent")
            .makeAccessible()
            .set(record, intent)
    }

    private data class RecoveredIntent(val token: String, val intent: Intent)
}
