package dev.wick.ui.content.m3

import androidx.compose.ui.graphics.Color

/**
 * Per-category accent colors for the settings grid.
 *
 * A single accent applied to every tile reads as a themed corporate app; giving each category its
 * own hue is what makes a 2D-style launcher grid feel illustrated. Hues are chosen to stay
 * distinguishable on the light surface and to keep dark text legible on the tile icon.
 */
object CategoryPalette {

    /** Tinted icon background, keyed by category id. */
    private val fills = mapOf(
        "chat" to Color(0xFFFFD9E2),
        "contacts_groups" to Color(0xFFD7E8FF),
        "payment" to Color(0xFFFFE9C7),
        "moments" to Color(0xFFFFDDE7),
        "system_privacy" to Color(0xFFE3DBFF),
        "voip" to Color(0xFFCCF0E4),
        "notifications" to Color(0xFFFFF0C2),
        "beautify" to Color(0xFFFFD8E6),
        "official_accounts" to Color(0xFFDDE9FF),
        "miniapps" to Color(0xFFE0DDFF),
        "channels" to Color(0xFFFFDCCC),
        "profile" to Color(0xFFFFD9E2),
        "debug" to Color(0xFFE2E2E2),
        "scripting_java" to Color(0xFFD4EDD9),
        "scripting_python" to Color(0xFFD6E8FF),
        "entertain" to Color(0xFFFFE0F0),
        "batch" to Color(0xFFFFE6C4),
        "home_screen_menu" to Color(0xFFE8DBFF),
        "contact_details" to Color(0xFFD9F0EA),
    )

    /** Icon foreground, deliberately darker than the fill so the glyph stays readable. */
    private val inks = mapOf(
        "chat" to Color(0xFF9D174D),
        "contacts_groups" to Color(0xFF1E40AF),
        "payment" to Color(0xFF92400E),
        "moments" to Color(0xFF9D174D),
        "system_privacy" to Color(0xFF4C1D95),
        "voip" to Color(0xFF065F46),
        "notifications" to Color(0xFF854D0E),
        "beautify" to Color(0xFF9D174D),
        "official_accounts" to Color(0xFF1E3A8A),
        "miniapps" to Color(0xFF3730A3),
        "channels" to Color(0xFF9A3412),
        "profile" to Color(0xFF9D174D),
        "debug" to Color(0xFF374151),
        "scripting_java" to Color(0xFF166534),
        "scripting_python" to Color(0xFF1E40AF),
        "entertain" to Color(0xFF86198F),
        "batch" to Color(0xFF92400E),
        "home_screen_menu" to Color(0xFF5B21B6),
        "contact_details" to Color(0xFF115E59),
    )

    private val fallbackFill = Color(0xFFFFD9E2)
    private val fallbackInk = Color(0xFF9D174D)

    fun fillFor(categoryId: String): Color = fills[categoryId] ?: fallbackFill

    fun inkFor(categoryId: String): Color = inks[categoryId] ?: fallbackInk
}
