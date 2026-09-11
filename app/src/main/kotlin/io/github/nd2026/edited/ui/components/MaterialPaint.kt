package io.github.nd2026.edited.ui.components

import java.awt.Color

internal fun composite(base: Color, overlay: Color): Color {
    val alpha = overlay.alpha / 255f
    val inverse = 1f - alpha
    return Color(
        (overlay.red * alpha + base.red * inverse).toInt().coerceIn(0, 255),
        (overlay.green * alpha + base.green * inverse).toInt().coerceIn(0, 255),
        (overlay.blue * alpha + base.blue * inverse).toInt().coerceIn(0, 255),
    )
}

internal fun Color.withAlpha(alpha: Int): Color = Color(red, green, blue, alpha.coerceIn(0, 255))
