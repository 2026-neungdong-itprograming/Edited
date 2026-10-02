package io.github.nd2026.edited.ui

import io.github.nd2026.edited.theme.Theme
import java.awt.Color

/**
 * Assigns a color per broad part-of-speech category, purely so [TextAreaWidget] can visually
 * confirm the Kiwi pipeline ([io.github.nd2026.edited.lexer.MorphemeIndex]) is producing real
 * morphemes before any dedicated linter/highlighting feature exists.
 *
 * Colors are derived from [Theme.onSurface]'s brightness rather than hardcoded per-theme, so
 * they stay readable in both the light and dark theme without a second palette to maintain.
 */
internal object PosHighlight {

    private enum class Category(val hue: Float) {
        NOUN(0.58f),
        PREDICATE(0.33f),
        MODIFIER(0.80f),
        PARTICLE(0.02f),
        FOREIGN(0.11f),
        /** Plain punctuation/whitespace - drawn in the theme's normal text color, no tint. */
        SYMBOL(-1f),
    }

    // Kiwi's POS tag names (Sejong-style) - kept as plain strings (rather than depending on the
    // binding's byte constants here) since Morpheme.partOfSpeech is already just its name.
    private fun categoryOf(partOfSpeech: String): Category = when (partOfSpeech) {
        "NNG", "NNP", "NNB", "NP", "NR" -> Category.NOUN
        "VV", "VA", "VX", "VCN", "VCP" -> Category.PREDICATE
        "MM", "MAG", "MAJ" -> Category.MODIFIER
        "XSN", "XSA", "XSV", "XSM", "XPN", "XR" -> Category.PARTICLE
        "SF", "SP", "SS", "SSO", "SSC", "SE", "SO", "SW", "SB" -> Category.SYMBOL
        else -> when {
            partOfSpeech.startsWith("J") || partOfSpeech.startsWith("E") -> Category.PARTICLE // JK*/JX/JC, EP/EF/EC/ETN/ETM
            else -> Category.FOREIGN // SH/SL/SN/IC/W_*/Z_*/UNKNOWN - notably "not a plain word"
        }
    }

    fun colorFor(theme: Theme, partOfSpeech: String): Color {
        val category = categoryOf(partOfSpeech)
        if (category == Category.SYMBOL) return theme.onSurface

        val hsb = FloatArray(3)
        Color.RGBtoHSB(theme.onSurface.red, theme.onSurface.green, theme.onSurface.blue, hsb)
        return Color.getHSBColor(category.hue, 0.55f, hsb[2].coerceIn(0.6f, 0.95f))
    }
}
