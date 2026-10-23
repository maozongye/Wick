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
import dev.wick.utils.android.showToastSuspend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object DownloadImagesToLocalStorage : SwitchFeature(), WeChatMessageContextMenuApi.IMenuItemsProvider {

    override val technicalId = "图片保存到本地"
    override val nameRes = R.string.feature_download_images_to_local_storage_name
    override val categoryIds = listOf(FeatureCategoryIds.CHAT)
    override val descriptionRes = R.string.feature_download_images_to_local_storage_description

    private const val TAG = "DownloadImagesToLocalStorage"

    override fun onEnable() {
        WeChatMessageContextMenuApi.addProvider(this)
    }

    override fun onDisable() {
        WeChatMessageContextMenuApi.removeProvider(this)
    }

    override fun getMenuItems(): List<WeChatMessageContextMenuApi.MenuItem> {
        return listOf(
            WeChatMessageContextMenuApi.MenuItem(
                777021,
                localizedChatString(R.string.chat_action_download),
                DownloadIcon,
                MaterialSymbols.Outlined.Download,
                { msgInfo -> msgInfo.type == MessageType.IMAGE }
            ) { _, _, msgInfo ->
                CoroutineScope(Dispatchers.IO).launch {
                    val path = WeMessageApi.downloadImage(msgInfo.serverId) ?: run {
                        WeLogger.e(TAG, "failed to cache & download image")
                        showToastSuspend(localizedChatString(R.string.chat_image_download_failed))
                        return@launch
                    }
                    showToastSuspend(localizedChatString(R.string.chat_image_download_success, path))
                }
            }
        )
    }
}
