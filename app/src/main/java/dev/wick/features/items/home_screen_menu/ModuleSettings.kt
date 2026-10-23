package dev.wick.features.items.home_screen_menu

import com.tencent.mm.ui.LauncherUI
import dev.wick.R
import dev.wick.i18n.HostLocalizedStrings
import dev.wick.BuildConfig
import dev.wick.features.api.ui.WeHomeScreenPopupMenuApi
import dev.wick.features.api.ui.WeSettingsInjector
import dev.wick.features.core.FeatureCategoryIds
import dev.wick.features.core.SwitchFeature
import dev.wick.ui.utils.ExtensionIcon
import dev.wick.utils.HookParam

object ModuleSettings : SwitchFeature(), WeHomeScreenPopupMenuApi.IMenuItemsProvider {

    override val technicalId = "模块设置"
    override val nameRes = R.string.feature_module_settings_name
    override val categoryIds = listOf(FeatureCategoryIds.HOME_SCREEN_MENU)
    override val descriptionRes = R.string.feature_module_settings_description

    override fun onEnable() {
        WeHomeScreenPopupMenuApi.addProvider(this)
    }

    override fun onDisable() {
        WeHomeScreenPopupMenuApi.removeProvider(this)
    }

    override fun getMenuItems(param: HookParam): List<WeHomeScreenPopupMenuApi.MenuItem> =
        listOf(
            WeHomeScreenPopupMenuApi.MenuItem(
                0, HostLocalizedStrings.get(R.string.app_name), ExtensionIcon
            ) { WeSettingsInjector.openSettingsDialog(LauncherUI.getInstance()!!) }
        )
}
