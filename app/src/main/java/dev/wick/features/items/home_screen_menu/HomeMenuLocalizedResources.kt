package dev.wick.features.items.home_screen_menu

import androidx.annotation.StringRes
import dev.wick.i18n.LocaleResourceMode
import dev.wick.i18n.LocalizedContextFactory
import dev.wick.i18n.WeKitLocaleController
import dev.wick.utils.HostInfo

fun localizedHomeMenuString(@StringRes id: Int, vararg formatArgs: Any): String =
    LocalizedContextFactory.create(
        HostInfo.application,
        WeKitLocaleController.resolvedLocale,
        LocaleResourceMode.InjectedHost,
    ).getString(id, *formatArgs)
