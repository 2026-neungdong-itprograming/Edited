package io.github.nd2026.edited.ui

import io.github.nd2026.edited.core.TextArea
import io.github.nd2026.edited.core.TextAreaListener
import io.github.nd2026.edited.core.TextEdit
import io.github.nd2026.edited.event.InputEvent
import io.github.nd2026.edited.render.PixelMetrics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.event.KeyEvent

/**
 * Toolkit-native text editor widget. Replaces the old plain-Swing `TextAreaView`, which
 * repainted the whole document on every keystroke and ignored `TextArea`'s existing
 * `Viewport`/`visibleText` virtualization API entirely. This widget actually uses it: every
 * paint computes the on-screen line range via [PixelMetrics] and asks the framework-agnostic
 * [TextArea] core for only that text, so painting cost stays proportional to the viewport, not
 * the document - the whole point of supporting 1M-10M character documents smoothly.
 */
class TextAreaWidget(private val textArea: TextArea = TextArea()) : Widget() {

    private var scrollY = 0
    private var lastLineHeight = 20

    init {
        focusable = true
        textArea.addListener(object : TextAreaListener {
            override fun onTextChanged(edit: TextEdit) = requestRepaint()
            override fun onCaretMoved(offset: Int) = requestRepaint()
        })
    }

    override fun onPaint(g: Graphics2D, localRegion: Rectangle) {
        val t = theme
        g.color = t.surface
        g.fillRect(0, 0, bounds.width, bounds.height)

        g.font = t.monospaceFont
        val fm = g.fontMetrics
        val metrics = PixelMetrics(fm.height, fm.ascent)
        lastLineHeight = metrics.lineHeight

        val viewport = metrics.viewportFor(scrollY, bounds.height, textArea.lineCount)
        val visibleRange = textArea.visibleLineRange(viewport)
        if (visibleRange.isEmpty()) return

        g.color = t.onSurface
        var line = visibleRange.first
        for (lineText in textArea.visibleText(viewport).split('\n')) {
            if (line !in visibleRange) break
            g.drawString(lineText, 4, metrics.baselineOf(line) - scrollY)
            line++
        }

        val caretLine = textArea.lineOf(textArea.caret)
        if (caretLine in visibleRange) {
            val caretCol = textArea.caret - textArea.lineStart(caretLine)
            val caretX = 4 + fm.stringWidth(textArea.lineText(caretLine).take(caretCol))
            val caretY = metrics.topOf(caretLine) - scrollY
            g.color = t.primary
            g.drawLine(caretX, caretY + 2, caretX, caretY + metrics.lineHeight - 2)
        }
    }

    override fun onKeyEvent(event: InputEvent.KeyInput): Boolean {
        when (event) {
            is InputEvent.KeyTyped -> {
                val c = event.char
                if (c == '\b') {
                    val caret = textArea.caret
                    if (caret > 0) textArea.delete(caret - 1, caret)
                } else if (!c.isISOControl() || c == '\n') {
                    textArea.typeAtCaret(c.toString())
                }
                return true
            }

            is InputEvent.KeyPressed -> {
                when (event.keyCode) {
                    KeyEvent.VK_LEFT -> textArea.moveCaretTo(textArea.caret - 1)
                    KeyEvent.VK_RIGHT -> textArea.moveCaretTo(textArea.caret + 1)
                    else -> return false
                }
                return true
            }
        }
    }

    override fun onMouseEvent(event: InputEvent.MouseInput): Boolean {
        if (event !is InputEvent.Scroll) return false
        val maxScroll = (textArea.lineCount * lastLineHeight - bounds.height).coerceAtLeast(0)
        scrollY = (scrollY + (event.unitsToScroll * lastLineHeight).toInt()).coerceIn(0, maxScroll)
        requestRepaint()
        return true
    }
}
