package dev.wick.features.items.home_screen_menu

import com.tencent.mm.ui.LauncherUI
import dev.wick.R
import dev.wick.features.api.ui.WeHomeScreenPopupMenuApi
import dev.wick.features.core.FeatureCategoryIds
import dev.wick.features.core.SwitchFeature
import dev.wick.features.items.contacts.showOpenConversationDialog
import dev.wick.ui.utils.ChatInfoIcon
import dev.wick.utils.HookParam

object OpenConversationMenu : SwitchFeature(), WeHomeScreenPopupMenuApi.IMenuItemsProvider {

    override val technicalId = "跳转对话菜单"
    override val nameRes = R.string.feature_open_conversation_menu_name
    override val categoryIds = listOf(FeatureCategoryIds.HOME_SCREEN_MENU)
    override val descriptionRes = R.string.feature_open_conversation_menu_description

    override fun onEnable() {
        WeHomeScreenPopupMenuApi.addProvider(this)
    }

    override fun onDisable() {
        WeHomeScreenPopupMenuApi.removeProvider(this)
    }

    override fun getMenuItems(param: HookParam): List<WeHomeScreenPopupMenuApi.MenuItem> {
        return listOf(
            WeHomeScreenPopupMenuApi.MenuItem(
                777025, localizedHomeMenuString(R.string.home_menu_open_conversation), ChatInfoIcon
            ) {
                showOpenConversationDialog(LauncherUI.getInstance()!!)
            }
        )
    }
}
