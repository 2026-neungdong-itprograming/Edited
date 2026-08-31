package io.github.nd2026.edited.render

import io.github.nd2026.edited.core.Viewport
import java.awt.Rectangle

/**
 * Bridges pixel-space geometry (scroll offset, component height, line height) to the
 * line-based, pixel-ignorant [Viewport] the core operates on. [Viewport] itself stays
 * unchanged - the core must not know about pixels - this is purely a `.render`-layer adapter
 * a scrollbar/paint routine uses to compute what to ask `TextArea.visibleText(Viewport)` for.
 */
class PixelMetrics(val lineHeight: Int, val ascent: Int) {

    init {
        require(lineHeight > 0) { "lineHeight must be positive" }
    }

    /** The viewport visible for a given vertical scroll offset and viewport pixel height. */
    fun viewportFor(scrollY: Int, viewportHeight: Int, lineCount: Int): Viewport {
        val firstLine = (scrollY / lineHeight).coerceIn(0, lineCount)
        // +1 to cover a partially visible trailing line, +1 again so rounding never leaves the
        // last on-screen line unpainted.
        val visibleLines = (viewportHeight / lineHeight) + 2
        return Viewport(firstLine, visibleLines)
    }

    /** Converts a dirty pixel rectangle into the [Viewport] whose lines it overlaps. */
    fun viewportFor(dirty: Rectangle, lineCount: Int): Viewport {
        val firstLine = (dirty.y / lineHeight).coerceIn(0, lineCount)
        val lastLine = ((dirty.y + dirty.height) / lineHeight + 1).coerceIn(firstLine, lineCount)
        return Viewport(firstLine, lastLine - firstLine)
    }

    /** Y baseline (for `Graphics.drawString`) of the given line's text, relative to scrollY=0. */
    fun baselineOf(line: Int): Int = line * lineHeight + ascent

    /** Top pixel Y of the given line, relative to scrollY=0. */
    fun topOf(line: Int): Int = line * lineHeight
}
