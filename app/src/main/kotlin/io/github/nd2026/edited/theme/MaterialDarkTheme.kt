package io.github.nd2026.edited.theme

import java.awt.Color

/** Material 3 baseline dark color scheme, hardcoded (no JSON loading needed for two static themes). */
val MaterialDarkTheme = Theme(
    name = "Material Dark",
    primary = Color(0xC9, 0xC1, 0xFF),
    onPrimary = Color(0x32, 0x24, 0x89),
    surface = Color(0x1C, 0x1B, 0x1F),
    onSurface = Color(0xE6, 0xE1, 0xE6),
    background = Color(0x1C, 0x1B, 0x1F),
    onBackground = Color(0xE6, 0xE1, 0xE6),
    error = Color(0xFF, 0xB4, 0xAB),
    onError = Color(0x69, 0x00, 0x05),
    outline = Color(0x93, 0x8F, 0x99),
    bodyFont = ThemeFonts.of(14),
    labelFont = ThemeFonts.of(12),
    monospaceFont = ThemeFonts.of(16),
)
