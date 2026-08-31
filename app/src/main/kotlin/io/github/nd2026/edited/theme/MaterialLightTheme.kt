package io.github.nd2026.edited.theme

import java.awt.Color
import java.awt.Font

/** Material 3 baseline light color scheme, hardcoded (no JSON loading needed for two static themes). */
val MaterialLightTheme = Theme(
    name = "Material Light",
    primary = Color(0x65, 0x58, 0xF1),
    onPrimary = Color(0xFF, 0xFF, 0xFF),
    surface = Color(0xFF, 0xFB, 0xFE),
    onSurface = Color(0x1C, 0x1B, 0x1F),
    background = Color(0xFF, 0xFB, 0xFE),
    onBackground = Color(0x1C, 0x1B, 0x1F),
    error = Color(0xBA, 0x1A, 0x1A),
    onError = Color(0xFF, 0xFF, 0xFF),
    outline = Color(0x79, 0x74, 0x7E),
    bodyFont = Font(Font.SANS_SERIF, Font.PLAIN, 14),
    labelFont = Font(Font.SANS_SERIF, Font.PLAIN, 12),
    monospaceFont = Font(Font.MONOSPACED, Font.PLAIN, 16),
)
