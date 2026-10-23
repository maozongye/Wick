package dev.wick.features.items.system

import android.content.Context
import androidx.annotation.StringRes
import dev.wick.i18n.LocaleResourceMode
import dev.wick.i18n.LocalizedContextFactory
import dev.wick.i18n.WeKitLocaleController
import dev.wick.utils.HostInfo

fun localizedSystemString(@StringRes id: Int, vararg args: Any): String =
    HostInfo.application.systemLocalizedContext().getString(id, *args)

fun Context.localizedSystemString(@StringRes id: Int, vararg args: Any): String =
    systemLocalizedContext().getString(id, *args)

private fun Context.systemLocalizedContext(): Context = LocalizedContextFactory.create(
    this,
    WeKitLocaleController.resolvedLocale,
    LocaleResourceMode.InjectedHost,
)
