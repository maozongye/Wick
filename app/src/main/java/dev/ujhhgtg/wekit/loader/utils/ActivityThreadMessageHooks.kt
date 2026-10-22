package dev.ujhhgtg.wekit.loader.utils

import android.annotation.SuppressLint
import android.app.ActivityThread
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Message
import dev.ujhhgtg.reflekt.reflekt
import dev.ujhhgtg.reflekt.utils.makeAccessible
import dev.ujhhgtg.wekit.loader.utils.ActivityProxy.ActProxyMgr
import dev.ujhhgtg.wekit.loader.utils.ActivityProxy.IntentTokenCache
import dev.ujhhgtg.wekit.utils.HookHandle
import dev.ujhhgtg.wekit.utils.HookParam
import dev.ujhhgtg.wekit.utils.WeLogger
import dev.ujhhgtg.wekit.utils.hookBeforeDirectly

/**
 * Recovers the real intent from the launch message instead of replacing
 * `Handler.mCallback` on ActivityThread's main handler.
 *
 * The old approach installed a `Handler.Callback` into `mH.mCallback`; a host-side integrity
 * check reading that field sees a non-null value where the framework leaves it null. Hooking
 * `ActivityThread$H.handleMessage` leaves the field untouched and intercepts the same messages.
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

        val installed = runCatching {
            Class.forName("android.app.ActivityThread\$H")
                .reflekt()
                .firstMethodOrNull {
                    name = "handleMessage"
                    parameterCount = 1
                }
                ?.hookBeforeDirectly { interceptMessage() }
                ?.let(handles::add)

            handles.size == 1
        }.getOrElse {
            WeLogger.e(TAG, "failed to install ActivityThread\$H hook", it)
            false
        }

        if (installed) {
            WeLogger.i(TAG, "ActivityThread\$H.handleMessage hook installed")
        } else {
            handles.forEach { it.unhook() }
            handles.clear()
            WeLogger.w(TAG, "ActivityThread\$H.handleMessage hook unavailable")
        }
        return installed
    }

    private fun HookParam.interceptMessage() {
        val msg = args[0] as Message
        when (msg.what) {
            LAUNCH_ACTIVITY -> handleLaunchActivity(msg)
            EXECUTE_TRANSACTION -> handleExecuteTransaction(msg)
        }
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
                IntentTokenCache.remove(recovered.token)
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

                        IntentTokenCache.remove(recovered.token)
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
