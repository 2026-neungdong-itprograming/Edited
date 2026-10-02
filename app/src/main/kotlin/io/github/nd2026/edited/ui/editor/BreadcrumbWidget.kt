package io.github.nd2026.edited.ui.editor

import io.github.nd2026.edited.core.Scope
import io.github.nd2026.edited.core.ScopeResolver
import io.github.nd2026.edited.core.TextArea
import io.github.nd2026.edited.event.InputEvent
import io.github.nd2026.edited.ui.Semantics
import io.github.nd2026.edited.ui.Widget
import io.github.nd2026.edited.ui.components.withAlpha
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.event.MouseEvent

/**
 * Shows where the caret is in the manuscript - `작품 › 제1화 › 장면 2` - and jumps to a level when
 * clicked. The path is recomputed only when the caret changes line ([ScopeResolver] scans a bounded
 * number of lines backwards), never per frame.
 */
class BreadcrumbWidget(
    private val textArea: TextArea,
    private val documentTitle: () -> String,
    private val onNavigate: (line: Int) -> Unit,
) : Widget() {
    private var scopes: List<Scope> = emptyList()
    private var cachedLine = -1
    private var segmentEnds: List<Int> = emptyList()

    init {
        semantics = Semantics(role = Semantics.Role.NONE, name = "현재 위치")
    }

    /** Recomputes the path if the caret's line changed (or [force]); repaints only when it differs. */
    fun refresh(force: Boolean = false) {
        val line = textArea.lineOf(textArea.caret)
        if (!force && line == cachedLine) return
        cachedLine = line
        val next = ScopeResolver.resolve(textArea, line)
        if (next != scopes) {
            scopes = next
            requestRepaint()
        }
    }

    override fun onPaint(g: Graphics2D, localRegion: Rectangle) {
        g.color = theme.surface
        g.fillRect(0, 0, bounds.width, bounds.height)
        g.color = theme.outline.withAlpha(60)
        g.drawLine(0, bounds.height - 1, bounds.width, bounds.height - 1)

        g.font = theme.labelFont
        val fm = g.fontMetrics
        val baseline = (bounds.height + fm.ascent - fm.descent) / 2
        val separator = "  ›  "
        val labels = listOf(documentTitle()) + scopes.map { it.title }
        val ends = ArrayList<Int>(labels.size)
        var x = 14
        labels.forEachIndexed { index, label ->
            if (x > bounds.width) return@forEachIndexed
            val last = index == labels.lastIndex
            g.color = if (last) theme.onSurface else theme.outline
            val text = ellipsize(label, g, (bounds.width - x - 8).coerceAtLeast(0))
            g.drawString(text, x, baseline)
            x += fm.stringWidth(text)
            ends += x
            if (!last) {
                g.color = theme.outline.withAlpha(120)
                g.drawString(separator, x, baseline)
                x += fm.stringWidth(separator)
            }
        }
        segmentEnds = ends
    }

    override fun onMouseEvent(event: InputEvent.MouseInput): Boolean {
        if (event !is InputEvent.MousePressed || event.button != MouseEvent.BUTTON1) return false
        val localX = event.x - bounds.x
        val index = segmentEnds.indexOfFirst { localX <= it + SEPARATOR_SLOP }
        when {
            index < 0 -> return false
            index == 0 -> onNavigate(0) // the document title is the root of the path
            else -> scopes.getOrNull(index - 1)?.let { onNavigate(it.line) } ?: return false
        }
        return true
    }

    private fun ellipsize(text: String, g: Graphics2D, maxWidth: Int): String {
        val fm = g.fontMetrics
        if (fm.stringWidth(text) <= maxWidth) return text
        var end = text.length
        while (end > 0 && fm.stringWidth(text.take(end) + "…") > maxWidth) end--
        return text.take(end) + "…"
    }

    private companion object {
        const val SEPARATOR_SLOP = 12
    }
}
