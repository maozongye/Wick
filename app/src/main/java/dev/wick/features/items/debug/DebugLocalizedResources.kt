package dev.wick.features.items.debug

import android.content.Context
import androidx.annotation.StringRes
import dev.wick.i18n.LocaleResourceMode
import dev.wick.i18n.LocalizedContextFactory
import dev.wick.i18n.WeKitLocaleController
import dev.wick.utils.HostInfo

fun localizedDebugString(@StringRes id: Int, vararg args: Any): String =
    HostInfo.application.debugLocalizedContext().getString(id, *args)

fun Context.localizedDebugString(@StringRes id: Int, vararg args: Any): String =
    debugLocalizedContext().getString(id, *args)

private fun Context.debugLocalizedContext(): Context = LocalizedContextFactory.create(
    this,
    WeKitLocaleController.resolvedLocale,
    LocaleResourceMode.InjectedHost,
)
