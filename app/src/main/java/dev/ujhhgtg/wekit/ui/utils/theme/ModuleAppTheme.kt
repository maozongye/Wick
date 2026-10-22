package dev.ujhhgtg.wekit.ui.utils.theme

import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import dev.ujhhgtg.wekit.i18n.LocaleResourceMode
import dev.ujhhgtg.wekit.i18n.WeKitLocaleProvider

/**
 * Theme for the module's own app screens (MainActivity).
 *
 * Follows [ThemeSettings.themeMode] like [ModuleTheme] does, so the settings page and the app
 * shell cannot disagree about light vs. dark. Uses the fixed palettes from Color.kt rather than
 * a generated one: the module app is not re-themed by the user's seed choice, and a static
 * palette is one less thing that can drift from the design.
 */
@Composable
fun ModuleAppTheme(
    darkTheme: Boolean = ThemeSettings.themeMode.resolve(),
    content: @Composable () -> Unit
) {
    WeKitLocaleProvider(mode = LocaleResourceMode.ModuleApp) {
        val colorScheme = if (darkTheme) darkScheme else lightScheme
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.standard(),
        ) {
            content()
        }
    }
}
