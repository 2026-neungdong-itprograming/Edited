package io.github.nd2026.edited.ui.panes

import io.github.nd2026.edited.ui.Widget
import java.awt.Color
import java.awt.FontMetrics
import java.awt.Graphics2D

/** Shared look of the side/bottom panes (lead-owned; see PLAN.md). */
internal object PaneStyle {
    /** Height of the title row painted by [paintPanel]. */
    const val HEADER_HEIGHT = 38

    fun paintPanel(widget: Widget, g: Graphics2D, title: String) {
        val t = widget.theme
        val b = widget.bounds
        g.color = t.surface; g.fillRect(0, 0, b.width, b.height)
        g.color = t.separator; g.drawRect(0, 0, b.width - 1, b.height - 1)
        g.drawLine(0, HEADER_HEIGHT, b.width, HEADER_HEIGHT)
        g.font = t.labelFont.deriveFont(13f); g.color = t.onSurface; g.drawString(ellipsize(title, g.fontMetrics, b.width - 28), 14, 25)
    }

    fun blend(base: Color, overlay: Color, alpha: Float): Color {
        val inverse = 1f - alpha
        return Color((base.red * inverse + overlay.red * alpha).toInt(), (base.green * inverse + overlay.green * alpha).toInt(), (base.blue * inverse + overlay.blue * alpha).toInt())
    }

    /** Rows to move for a wheel/trackpad [units] value; never 0 so small trackpad deltas still scroll. */
    fun scrollRows(units: Double): Int = when {
        units > 0 -> maxOf(1, units.toInt())
        units < 0 -> minOf(-1, units.toInt())
        else -> 0
    }

    /** Breaks [text] into lines that fit in [width] pixels (per character, which suits Korean prose). */
    fun wrap(text: String, fm: FontMetrics, width: Int): List<String> = text.lines().flatMap { paragraph ->
        if (paragraph.isEmpty()) return@flatMap listOf("")
        val out = ArrayList<String>()
        val line = StringBuilder()
        for (c in paragraph) {
            if (line.isNotEmpty() && fm.stringWidth(line.toString() + c) > width) { out += line.toString(); line.setLength(0) }
            line.append(c)
        }
        out += line.toString()
        out
    }

    /** Cuts [text] with an ellipsis so it fits in [width] pixels. */
    fun ellipsize(text: String, fm: FontMetrics, width: Int): String {
        if (fm.stringWidth(text) <= width) return text
        var lo = 0
        var hi = text.length
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (fm.stringWidth(text.substring(0, mid) + "…") <= width) lo = mid else hi = mid - 1
        }
        return text.substring(0, lo) + "…"
    }
}
