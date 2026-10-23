package dev.wick.features.items.home_screen_menu

import dev.wick.R
import dev.wick.features.api.core.WeConversationApi
import dev.wick.features.api.ui.WeHomeScreenPopupMenuApi
import dev.wick.features.core.FeatureCategoryIds
import dev.wick.features.core.SwitchFeature
import dev.wick.ui.utils.VisibilityIcon
import dev.wick.ui.utils.VisibilityOffIcon
import dev.wick.utils.HookParam

object ToggleAllConversationsVisibility : SwitchFeature(), WeHomeScreenPopupMenuApi.IMenuItemsProvider {

    override val technicalId = "显隐全部对话"
    override val nameRes = R.string.feature_toggle_all_conversations_visibility_name
    override val categoryIds = listOf(FeatureCategoryIds.HOME_SCREEN_MENU)
    override val descriptionRes = R.string.feature_toggle_all_conversations_visibility_description

    override fun onEnable() {
        WeHomeScreenPopupMenuApi.addProvider(this)
    }

    override fun onDisable() {
        WeHomeScreenPopupMenuApi.removeProvider(this)
    }

    override fun getMenuItems(param: HookParam): List<WeHomeScreenPopupMenuApi.MenuItem> {
        return listOf(
            WeHomeScreenPopupMenuApi.MenuItem(
                777010, localizedHomeMenuString(R.string.home_menu_show_conversations), VisibilityIcon
            ) {
                WeConversationApi.setAllConversationVisibility(true)
            },
            WeHomeScreenPopupMenuApi.MenuItem(
                777011, localizedHomeMenuString(R.string.home_menu_hide_conversations), VisibilityOffIcon
            ) {
                WeConversationApi.setAllConversationVisibility(false)
            },
        )
    }
}
