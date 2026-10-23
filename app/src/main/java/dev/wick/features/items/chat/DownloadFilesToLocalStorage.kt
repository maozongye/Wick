package dev.wick.features.items.chat

import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Download
import dev.wick.R
import dev.wick.features.api.core.WeMessageApi
import dev.wick.features.api.core.models.MessageType
import dev.wick.features.api.ui.WeChatMessageContextMenuApi
import dev.wick.features.core.FeatureCategoryIds
import dev.wick.features.core.SwitchFeature
import dev.wick.ui.utils.DownloadIcon
import dev.wick.utils.WeLogger
import dev.wick.utils.android.showToast
import dev.wick.utils.android.showToastSuspend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object DownloadFilesToLocalStorage : SwitchFeature(), WeChatMessageContextMenuApi.IMenuItemsProvider {

    override val technicalId = "文件下载到本地"
    override val nameRes = R.string.feature_download_files_to_local_storage_name
    override val categoryIds = listOf(FeatureCategoryIds.CHAT)
    override val descriptionRes = R.string.feature_download_files_to_local_storage_description

    private const val TAG = "DownloadFilesToLocalStorage"

    override fun onEnable() {
        WeChatMessageContextMenuApi.addProvider(this)
    }

    override fun onDisable() {
        WeChatMessageContextMenuApi.removeProvider(this)
    }

    override fun getMenuItems(): List<WeChatMessageContextMenuApi.MenuItem> {
        return listOf(
            WeChatMessageContextMenuApi.MenuItem(
                777022,
                localizedChatString(R.string.chat_action_download),
                DownloadIcon,
                MaterialSymbols.Outlined.Download,
                { msgInfo -> msgInfo.type == MessageType.FILE }
            ) { _, _, msgInfo ->
                showToast(localizedChatString(R.string.chat_file_download_preparing))
                CoroutineScope(Dispatchers.IO).launch {
                    val path = WeMessageApi.downloadFile(msgInfo.instance) ?: run {
                        WeLogger.e(TAG, "failed to cache & download file")
                        showToastSuspend(localizedChatString(R.string.chat_file_download_failed))
                        return@launch
                    }
                    showToastSuspend(localizedChatString(R.string.chat_file_download_success, path))
                }
            }
        )
    }
}
