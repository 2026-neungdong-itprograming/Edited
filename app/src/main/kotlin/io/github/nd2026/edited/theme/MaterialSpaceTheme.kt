package io.github.nd2026.edited.theme

import java.awt.Color

/**
 * Material Theme UI's "Material Space" (`themes/space.theme.json` and `colors/Material Space.xml`):
 * navy #1B2240 background, #EFEFF1 text, #959DAA secondary text and a lavender #AD9BF6 accent.
 */
val MaterialSpaceTheme = Theme(
    name = "Material Space",
    primary = Color(0xAD, 0x9B, 0xF6),
    onPrimary = Color(0x06, 0x0F, 0x2F),
    surface = Color(0x1B, 0x22, 0x40),
    onSurface = Color(0xEF, 0xEF, 0xF1),
    background = Color(0x1B, 0x22, 0x40),
    onBackground = Color(0xEF, 0xEF, 0xF1),
    error = Color(0xF0, 0x71, 0x78),
    onError = Color(0x06, 0x0F, 0x2F),
    outline = Color(0x95, 0x9D, 0xAA),
    bodyFont = ThemeFonts.of(14),
    labelFont = ThemeFonts.of(12),
    monospaceFont = ThemeFonts.of(16),
    warning = Color(0xFF, 0xCB, 0x6B),
)
