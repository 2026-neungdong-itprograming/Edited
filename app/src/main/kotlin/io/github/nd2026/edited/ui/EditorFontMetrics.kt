package io.github.nd2026.edited.ui

import java.awt.Font
import java.awt.FontMetrics
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.util.concurrent.ConcurrentHashMap

/**
 * Font metrics available before the first paint, so the editor, gutter and inspection stripe agree on
 * line height without waiting for a `Graphics2D`. Uses the same text-antialiasing hint as
 * [io.github.nd2026.edited.render.Surface], which is what makes the numbers match the painted text.
 */
internal object EditorFontMetrics {
    private val scratch = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics().apply {
        setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    }
    private val cache = ConcurrentHashMap<Font, FontMetrics>()

    fun of(font: Font): FontMetrics = cache.computeIfAbsent(font) {
        synchronized(scratch) { scratch.getFontMetrics(it) }
    }
}
