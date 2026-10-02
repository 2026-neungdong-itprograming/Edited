package io.github.nd2026.edited.ui.editor

import io.github.nd2026.edited.core.Severity
import io.github.nd2026.edited.core.InspectionModel
import io.github.nd2026.edited.event.InputEvent
import io.github.nd2026.edited.ui.EditorFontMetrics
import io.github.nd2026.edited.ui.Semantics
import io.github.nd2026.edited.ui.TextAreaWidget
import io.github.nd2026.edited.ui.Widget
import io.github.nd2026.edited.ui.components.withAlpha
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.event.MouseEvent

/**
 * Left margin of an editor: paragraph (line) numbers and the most severe inspection of each line.
 * Paints only the visible lines, reading scroll position from the [editor] it sits beside, so its
 * cost does not depend on manuscript length. Clicking a number selects that paragraph.
 */
class GutterWidget(
    private val editor: TextAreaWidget,
    private val inspections: InspectionModel,
) : Widget() {

    init {
        semantics = Semantics(role = Semantics.Role.NONE, name = "문단 번호")
    }

    /** Width needed for the current line count, so the editor can lay itself out beside the gutter. */
    fun preferredWidth(): Int {
        val digits = editor.textArea.lineCount.toString().length.coerceAtLeast(3)
        val fm = EditorFontMetrics.of(theme.labelFont)
        return LEFT_PAD + MARKER_SLOT + digits * fm.charWidth('0') + RIGHT_PAD
    }

    override fun onPaint(g: Graphics2D, localRegion: Rectangle) {
        g.color = theme.surface
        g.fillRect(0, 0, bounds.width, bounds.height)
        g.color = theme.outline.withAlpha(60)
        g.drawLine(bounds.width - 1, 0, bounds.width - 1, bounds.height)

        val textArea = editor.textArea
        val lh = editor.lineHeight
        val scrollY = editor.scrollY
        val first = (scrollY / lh).coerceIn(0, textArea.lineCount - 1)
        val last = ((scrollY + bounds.height) / lh).coerceAtMost(textArea.lineCount - 1)
        val caretLine = textArea.lineOf(textArea.caret)

        g.font = theme.labelFont
        val fm = g.fontMetrics
        for (line in first..last) {
            val top = line * lh - scrollY
            if (line == caretLine) {
                g.color = theme.primary.withAlpha(22)
                g.fillRect(0, top, bounds.width - 1, lh)
            }
            inspections.worstOnLine(line)?.let { paintMarker(g, it.severity, top, lh) }
            val label = (line + 1).toString()
            g.color = if (line == caretLine) theme.onSurface else theme.outline
            g.drawString(label, bounds.width - RIGHT_PAD - fm.stringWidth(label), top + (lh + fm.ascent - fm.descent) / 2)
        }
    }

    private fun paintMarker(g: Graphics2D, severity: Severity, top: Int, lineHeight: Int) {
        val size = 8
        g.color = theme.colorOf(severity)
        g.fillOval(LEFT_PAD, top + (lineHeight - size) / 2, size, size)
    }

    override fun onMouseEvent(event: InputEvent.MouseInput): Boolean {
        if (event !is InputEvent.MousePressed || event.button != MouseEvent.BUTTON1) return false
        val textArea = editor.textArea
        val line = ((event.y - bounds.y + editor.scrollY) / editor.lineHeight).coerceIn(0, textArea.lineCount - 1)
        textArea.breakUndoGroup()
        textArea.moveCaretTo(textArea.lineStart(line))
        textArea.moveCaretTo((textArea.lineEnd(line) + 1).coerceAtMost(textArea.length), extendSelection = true)
        return true
    }

    private companion object {
        const val LEFT_PAD = 8
        const val MARKER_SLOT = 14
        const val RIGHT_PAD = 10
    }
}
