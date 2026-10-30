package io.github.nd2026.edited.ui

import io.github.nd2026.edited.core.TextArea
import io.github.nd2026.edited.event.InputEvent
import io.github.nd2026.edited.lexer.Morpheme
import io.github.nd2026.edited.lexer.MorphemeAnalyzer
import java.awt.Rectangle
import java.awt.event.InputEvent as AwtInputEvent
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EditorZoomTest {
    private object Plain : MorphemeAnalyzer {
        override fun analyze(text: String, baseOffset: Int): List<Morpheme> = emptyList()
    }

    @Test
    fun ctrlWheelZoomsAndPlainWheelScrolls() {
        val w = TextAreaWidget(TextArea((1..200).joinToString("\n") { "${it}번째 줄" }), Plain).apply { setBounds(0, 0, 600, 400) }
        val base = w.editorFont.size2D
        val baseHeight = w.lineHeight
        var reported = 0f
        w.onFontSizeChanged = { reported = it }

        repeat(3) { w.onMouseEvent(InputEvent.Scroll(10, 10, -1.0, AwtInputEvent.CTRL_DOWN_MASK)) }
        assertEquals(base + 3, w.editorFont.size2D)
        assertEquals(base + 3, reported)
        assertTrue(w.lineHeight > baseHeight)
        assertEquals(0, w.scrollY)

        w.onMouseEvent(InputEvent.Scroll(10, 10, 1.0, AwtInputEvent.CTRL_DOWN_MASK))
        assertEquals(base + 2, w.editorFont.size2D)

        // The badge paints without trouble; a plain wheel still scrolls instead of zooming.
        val image = BufferedImage(600, 400, BufferedImage.TYPE_INT_ARGB)
        w.paint(image.createGraphics(), Rectangle(0, 0, 600, 400))
        w.onMouseEvent(InputEvent.Scroll(10, 10, 3.0, 0))
        assertEquals(base + 2, w.editorFont.size2D)
        assertTrue(w.scrollY > 0)

        w.fontSize = 500f
        assertEquals(72f, w.editorFont.size2D)
    }
}
