package io.github.nd2026.edited.theme

import java.awt.Color
import java.awt.Font

/**
 * Minimal Material-style design tokens. Widgets read colors/fonts from a [Theme] instead of
 * hardcoding [Color]/[Font] values, so switching [MaterialLightTheme]/[MaterialDarkTheme] (or a
 * future custom theme) recolors the whole tree. Deliberately not FlatLaf/Compose - a small,
 * explicit token set is enough for this pass.
 */
data class Theme(
    val name: String,
    // Material 3 baseline color roles.
    val primary: Color,
    val onPrimary: Color,
    val surface: Color,
    val onSurface: Color,
    val background: Color,
    val onBackground: Color,
    val error: Color,
    val onError: Color,
    val outline: Color,
    // Typography.
    val bodyFont: Font,
    val labelFont: Font,
    val monospaceFont: Font,
    // Elevation: Java2D has no real shadow rendering here, so elevation is approximated as an
    // alpha overlay tint per level (index 0 = resting surface, higher = more "raised").
    val elevationTints: List<Color> = defaultElevationTints(primary),
) {
    companion object {
        /** Material's elevation overlay: an alpha wash of [primary] over the surface, one entry per elevation level. */
        private fun defaultElevationTints(primary: Color): List<Color> =
            listOf(0, 5, 8, 11, 12, 14).map { alphaPercent ->
                Color(primary.red, primary.green, primary.blue, (alphaPercent * 255 / 100).coerceIn(0, 255))
            }
    }
}
