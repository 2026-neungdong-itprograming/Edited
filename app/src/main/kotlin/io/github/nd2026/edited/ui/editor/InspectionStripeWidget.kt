package io.github.nd2026.edited.ui.editor

import io.github.nd2026.edited.core.InspectionModel
import io.github.nd2026.edited.core.Severity
import io.github.nd2026.edited.event.InputEvent
import io.github.nd2026.edited.ui.Semantics
import io.github.nd2026.edited.ui.TextAreaWidget
import io.github.nd2026.edited.ui.Widget
import io.github.nd2026.edited.ui.components.withAlpha
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.event.MouseEvent
import kotlin.math.abs

/**
 * The editor's vertical scrollbar, with the whole document's inspections drawn as ticks along it
 * (IntelliJ's "error stripe"). The thumb shows the visible window; dragging scrolls, and clicking a
 * tick jumps the caret to that finding. Marker positions are proportional to line index, so drawing
 * is O(markers) however long the manuscript is.
 */
class InspectionStripeWidget(
    private val editor: TextAreaWidget,
    private val inspections: InspectionModel,
) : Widget() {
    private var dragging = false

    init {
        semantics = Semantics(role = Semantics.Role.NONE, name = "검수 표시 막대")
    }

    override fun onPaint(g: Graphics2D, localRegion: Rectangle) {
        g.color = theme.surface
        g.fillRect(0, 0, bounds.width, bounds.height)

        val thumb = thumbBounds()
        g.color = theme.onSurface.withAlpha(if (dragging || hovered) 56 else 34)
        g.fillRoundRect(1, thumb.y, bounds.width - 2, thumb.height, 6, 6)

        val lineCount = editor.textArea.lineCount
        // Least severe first so errors are drawn on top of warnings that fall on the same pixel row.
        for (severity in Severity.entries) {
            g.color = theme.colorOf(severity)
            for (marker in inspections.markers) {
                if (marker.severity != severity) continue
                g.fillRect(2, yOfLine(marker.line, lineCount), bounds.width - 4, MARKER_HEIGHT)
            }
        }
    }

    override fun onMouseEvent(event: InputEvent.MouseInput): Boolean = when (event) {
        is InputEvent.MousePressed -> {
            if (event.button != MouseEvent.BUTTON1) false else {
                val marker = markerNear(event.y - bounds.y)
                if (marker != null) {
                    editor.textArea.breakUndoGroup()
                    editor.textArea.moveCaretTo(editor.textArea.lineStart(marker))
                } else {
                    dragging = true
                    scrollToPixel(event.y - bounds.y)
                }
                true
            }
        }
        is InputEvent.MouseDragged -> if (dragging) { scrollToPixel(event.y - bounds.y); true } else false
        is InputEvent.MouseReleased, is InputEvent.MouseCancelled -> { val was = dragging; dragging = false; requestRepaint(); was }
        else -> false
    }

    private fun yOfLine(line: Int, lineCount: Int): Int =
        ((line.toLong() * (bounds.height - MARKER_HEIGHT)) / lineCount.coerceAtLeast(1)).toInt()

    private fun markerNear(localY: Int): Int? {
        val lineCount = editor.textArea.lineCount
        return inspections.markers
            .filter { abs(yOfLine(it.line, lineCount) + MARKER_HEIGHT / 2 - localY) <= HIT_SLOP }
            .minByOrNull { abs(yOfLine(it.line, lineCount) - localY) }
            ?.line
    }

    private fun totalHeight(): Int = (editor.textArea.lineCount * editor.lineHeight).coerceAtLeast(1)

    private fun thumbBounds(): Rectangle {
        val total = totalHeight()
        val viewport = editor.bounds.height
        val height = (bounds.height.toLong() * viewport / total).toInt().coerceIn(MIN_THUMB, bounds.height)
        val maxScroll = (total - viewport).coerceAtLeast(1)
        val y = ((bounds.height - height).toLong() * editor.scrollY / maxScroll).toInt().coerceIn(0, bounds.height - height)
        return Rectangle(0, y, bounds.width, height)
    }

    /** Centres the thumb on [localY]. */
    private fun scrollToPixel(localY: Int) {
        val thumb = thumbBounds()
        val track = (bounds.height - thumb.height).coerceAtLeast(1)
        val fraction = ((localY - thumb.height / 2).coerceIn(0, track)).toDouble() / track
        val maxScroll = (totalHeight() - editor.bounds.height).coerceAtLeast(0)
        editor.scrollTo(y = (fraction * maxScroll).toInt())
        requestRepaint()
    }

    private companion object {
        const val MARKER_HEIGHT = 3
        const val HIT_SLOP = 4
        const val MIN_THUMB = 24
    }
}
