package io.github.nd2026.edited.ui

import io.github.nd2026.edited.core.TextArea
import io.github.nd2026.edited.event.InputEvent
import io.github.nd2026.edited.lexer.Morpheme
import io.github.nd2026.edited.lexer.MorphemeAnalyzer
import java.awt.Rectangle
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DirectiveFoldingTest {
    private object Plain : MorphemeAnalyzer {
        override fun analyze(text: String, baseOffset: Int): List<Morpheme> = emptyList()
    }

    private fun paint(w: Widget) {
        val image = BufferedImage(w.bounds.width, w.bounds.height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        w.paint(g, Rectangle(0, 0, w.bounds.width, w.bounds.height))
        g.dispose()
    }

    private fun clickAt(w: TextAreaWidget, x: Int) {
        val y = w.bounds.y + w.lineHeight / 2
        w.onMouseEvent(InputEvent.MousePressed(x, y, MouseEvent.BUTTON1, 0))
        w.onMouseEvent(InputEvent.MouseReleased(x, y, MouseEvent.BUTTON1, 0))
    }

    @Test
    fun clickingTheChipUnfoldsAndTextAfterItStaysClickable() {
        val directive = "@Suppress(\"맞춤법\")"
        val area = TextArea("가 $directive 됬다.\n둘째 줄")
        area.moveCaretTo(area.length)
        val w = TextAreaWidget(area, Plain).apply { setBounds(0, 0, 800, 200) }
        paint(w)

        // Folded, the directive takes one chip: clicking just past it lands after the directive, not inside it.
        val prefix = "가 "
        val font = w.theme.monospaceFont
        val frc = java.awt.font.FontRenderContext(null, true, true)
        val prefixWidth = font.getStringBounds(prefix, frc).width.toInt()
        clickAt(w, 8 + prefixWidth + 24 + 2)
        assertTrue(area.caret >= prefix.length + directive.length, "caret ${area.caret}")

        // Clicking the chip puts the caret inside the directive, which unfolds it.
        clickAt(w, 8 + prefixWidth + 12)
        assertEquals(prefix.length + 1, area.caret)
        paint(w)

        w.revealDirectives = true
        paint(w)
    }
}
