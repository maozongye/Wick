package dev.wick.features.items.debug

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import dev.wick.R
import dev.wick.dexkit.cache.DexCacheManager
import dev.wick.features.core.ClickableFeature
import dev.wick.features.core.FeatureCategoryIds
import dev.wick.ui.content.AlertDialogContent
import dev.wick.ui.content.Button
import dev.wick.ui.content.TextButton
import dev.wick.ui.utils.showComposeDialog
import dev.wick.utils.android.showToastSuspend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object ResetDexCache : ClickableFeature() {

    override val technicalId = "重置适配信息"
    override val nameRes = R.string.feature_reset_dex_cache_name
    override val categoryIds = listOf(FeatureCategoryIds.DEBUG)
    override val descriptionRes = R.string.feature_reset_dex_cache_description

    override fun onClick(context: ComponentActivity) {
        showComposeDialog(context) {
            AlertDialogContent(
                title = { Text(stringResource(R.string.debug_reset_dex_cache_title)) },
                text = {
                    Text(stringResource(R.string.debug_reset_dex_cache_confirmation))
                },
                dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
                confirmButton = {
                    Button(onClick = {
                        CoroutineScope(Dispatchers.IO).launch {
                            showToastSuspend(localizedDebugString(R.string.debug_reset_dex_cache_clearing))
                            DexCacheManager.clearAllCache()
                            showToastSuspend(localizedDebugString(R.string.debug_reset_dex_cache_success))
                            withContext(Dispatchers.Main) {
                                onDismiss()
                            }
                        }
                    }) { Text(stringResource(R.string.dialog_confirm)) }
                })
        }
    }

    override val noSwitchWidget = true
}
