package dev.wick.features.items.chat

import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Edit
import dev.wick.R
import dev.wick.features.api.core.WeMessageApi
import dev.wick.features.api.ui.WeChatMessageContextMenuApi
import dev.wick.features.core.FeatureCategoryIds
import dev.wick.features.core.SwitchFeature
import dev.wick.ui.utils.UndoIcon
import dev.wick.utils.BehaviorPacing
import dev.wick.utils.WeLogger
import dev.wick.utils.android.showToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object BatchRevoke : SwitchFeature(), WeChatMessageContextMenuApi.IMenuItemsProvider {

    override val technicalId = "批量撤回"
    override val nameRes = R.string.feature_batch_revoke_name
    override val categoryIds = listOf(FeatureCategoryIds.CHAT)
    override val descriptionRes = R.string.feature_batch_revoke_description

    override val automationRisk = true

    private const val TAG = "BatchRevoke"

    /** Humanised pacing and hard quotas for burst revocations. */
    private const val REVOKE_MEDIAN_MS = 3_000L
    private const val REVOKE_WINDOW_LIMIT = 20
    private const val REVOKE_WINDOW_MS = 30 * 60 * 1000L
    private const val REVOKE_DAILY_LIMIT = 100

    override fun onEnable() {
        WeChatMessageContextMenuApi.addProvider(this)
    }

    override fun onDisable() {
        WeChatMessageContextMenuApi.removeProvider(this)
    }

    override fun getMenuItems(): List<WeChatMessageContextMenuApi.MenuItem> {
        return listOf(
            WeChatMessageContextMenuApi.MenuItem(
                777023, localizedChatString(R.string.chat_batch_revoke_menu), UndoIcon, MaterialSymbols.Outlined.Edit,
                isSupported = { false },
                // revokes then loads one message's text into the input box; single-message only
                multiSelect = WeChatMessageContextMenuApi.MultiSelectSupport.Adapted(
                    isSupported = { true },
                    onClick = { view, _, msgs ->
                        // yeah i know this is very cursed; this is the sequelae of writing too much python
                        // Burst revocations used to fire back-to-back on the main thread, which is
                        // both a fixed-period bot signature and an ANR risk; pace them on IO instead.
                        CoroutineScope(Dispatchers.IO).launch {
                            val allowed = msgs.takeWhile {
                                BehaviorPacing.reserve("revoke", BehaviorPacing.ageAdjustedLimit(REVOKE_WINDOW_LIMIT), REVOKE_WINDOW_MS) &&
                                    BehaviorPacing.reserveDaily("revoke", BehaviorPacing.ageAdjustedLimit(REVOKE_DAILY_LIMIT))
                            }
                            if (allowed.size < msgs.size) {
                                WeLogger.w(
                                    TAG,
                                    "revoke quota reached; ${msgs.size - allowed.size} of ${msgs.size} were skipped",
                                )
                            }
                            var succeeded = 0
                            allowed.forEachIndexed { index, msg ->
                                succeeded += runCatching { WeMessageApi.revokeMsg(msg); 1 }
                                    .getOrElse {
                                        WeLogger.e(TAG, "failed to revoke msgId=${msg.id}", it); 0
                                    }
                                if (index < allowed.size - 1) BehaviorPacing.pause(REVOKE_MEDIAN_MS)
                            }
                            showToast(
                                view.context,
                                view.context.localizedChatQuantity(
                                    R.plurals.chat_batch_revoke_result,
                                    msgs.size,
                                    succeeded,
                                    msgs.size,
                                ),
                            )
                        }
                    }
                )
            ) { _, _, _ ->
            }
        )
    }
}
