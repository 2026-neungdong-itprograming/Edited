package io.github.nd2026.edited.ui

import io.github.nd2026.edited.core.Inspection
import io.github.nd2026.edited.core.Severity
import io.github.nd2026.edited.core.TextArea
import io.github.nd2026.edited.event.InputEvent
import io.github.nd2026.edited.lexer.MorphemeAnalyzer
import io.github.nd2026.edited.lexer.Morpheme
import io.github.nd2026.edited.ui.docking.Axis
import io.github.nd2026.edited.ui.editor.EditorDocument
import io.github.nd2026.edited.ui.editor.EditorGroupWidget
import io.github.nd2026.edited.ui.editor.EditorPaneWidget
import java.awt.Rectangle
import java.awt.event.InputEvent as Awt
import java.awt.event.KeyEvent
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private object NoAnalyzer : MorphemeAnalyzer {
    override fun analyze(text: String, baseOffset: Int): List<Morpheme> = emptyList()
}

private val ctrl = Awt.CTRL_DOWN_MASK

class TextAreaWidgetTest {
    private fun widget(text: String = "", width: Int = 400, height: Int = 200) =
        TextAreaWidget(TextArea(text), NoAnalyzer).apply { setBounds(0, 0, width, height) }

    private fun paint(w: Widget) {
        val image = BufferedImage(w.bounds.width.coerceAtLeast(1), w.bounds.height.coerceAtLeast(1), BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        w.paint(g, Rectangle(0, 0, w.bounds.width, w.bounds.height))
        g.dispose()
    }

    @Test
    fun ctrlZAndShiftCtrlZUndoAndRedoTyping() {
        val w = widget()
        "hi".forEach { w.onKeyEvent(InputEvent.KeyTyped(it, 0)) }
        assertEquals("hi", w.textArea.snapshot())
        w.onKeyEvent(InputEvent.KeyPressed(KeyEvent.VK_Z, ctrl))
        assertEquals("", w.textArea.snapshot())
        w.onKeyEvent(InputEvent.KeyPressed(KeyEvent.VK_Z, ctrl or Awt.SHIFT_DOWN_MASK))
        assertEquals("hi", w.textArea.snapshot())
        w.onKeyEvent(InputEvent.KeyPressed(KeyEvent.VK_Z, ctrl))
        w.onKeyEvent(InputEvent.KeyPressed(KeyEvent.VK_Y, ctrl))
        assertEquals("hi", w.textArea.snapshot())
    }

    @Test
    fun imeCompositionThenCommitIsOneUndoUnit() {
        val w = widget()
        // Composition in progress: nothing is in the buffer yet, and undo must not touch anything.
        w.updateComposition("", "ㅎ", 1)
        w.updateComposition("", "하", 1)
        assertEquals("", w.textArea.snapshot())
        w.onKeyEvent(InputEvent.KeyPressed(KeyEvent.VK_Z, ctrl))
        w.updateComposition("한", "ㄱ", 1)
        w.updateComposition("글", "", 0)
        assertEquals("한글", w.textArea.snapshot())
        w.onKeyEvent(InputEvent.KeyPressed(KeyEvent.VK_Z, ctrl))
        assertEquals("", w.textArea.snapshot())
    }

    @Test
    fun imeCommitReplacingSelectionUndoesInOneStep() {
        val w = widget("가나다")
        w.textArea.moveCaretTo(0)
        w.textArea.moveCaretTo(2, extendSelection = true)
        w.updateComposition("한", "", 0)
        assertEquals("한다", w.textArea.snapshot())
        w.onKeyEvent(InputEvent.KeyPressed(KeyEvent.VK_Z, ctrl))
        assertEquals("가나다", w.textArea.snapshot())
    }

    @Test
    fun caretMovingOffscreenScrollsVerticallyToKeepItVisible() {
        val w = widget("line\n".repeat(200), height = 100)
        w.textArea.moveCaretTo(w.textArea.length)
        assertTrue(w.scrollY > 0)
        val caretTop = w.textArea.lineOf(w.textArea.caret) * w.lineHeight
        assertTrue(caretTop >= w.scrollY && caretTop + w.lineHeight <= w.scrollY + w.bounds.height)
        w.textArea.moveCaretTo(0)
        assertEquals(0, w.scrollY)
    }

    @Test
    fun longLinesScrollHorizontallyAndFollowTheCaret() {
        val w = widget("가".repeat(300), width = 300)
        paint(w) // paint measures the widest visible line, which defines the horizontal range
        assertEquals(0, w.scrollX)
        w.textArea.moveCaretTo(w.textArea.length)
        assertTrue(w.scrollX > 0, "caret at end of a long line must scroll right")
        w.textArea.moveCaretTo(0)
        assertEquals(0, w.scrollX)
    }

    @Test
    fun shiftWheelScrollsHorizontallyAndPlainWheelVertically() {
        val w = widget("가".repeat(300) + "\n" + "x\n".repeat(100), width = 300, height = 100)
        paint(w)
        w.onMouseEvent(InputEvent.Scroll(10, 10, 3.0, Awt.SHIFT_DOWN_MASK))
        assertTrue(w.scrollX > 0)
        assertEquals(0, w.scrollY)
        w.onMouseEvent(InputEvent.Scroll(10, 10, 3.0, 0))
        assertTrue(w.scrollY > 0)
    }

    @Test
    fun clickMapsToTheRightColumnAfterHorizontalScrolling() {
        val w = widget("0123456789".repeat(40), width = 200)
        paint(w)
        w.scrollTo(x = 150)
        w.onMouseEvent(InputEvent.MousePressed(10, 5, java.awt.event.MouseEvent.BUTTON1, 0))
        // 10px into a view scrolled by 150px lands past column 0; at the font's width this is a few columns in.
        assertTrue(w.textArea.caret in 5..40, "caret=${w.textArea.caret}")
    }
}

class EditorGroupWidgetTest {
    private fun document(id: String, text: String = "본문\n".repeat(50)) = EditorDocument(id, "$id.md", TextArea(text))

    private fun group(width: Int = 1000, height: Int = 600): EditorGroupWidget =
        EditorGroupWidget(NoAnalyzer).apply {
            setBounds(0, 0, width, height)
            open(document("a"))
        }

    @Test
    fun splitRightShowsTheSameDocumentInTwoSideBySidePanes() {
        val g = group()
        assertEquals(1, g.leafCount)
        assertTrue(g.split(Axis.HORIZONTAL))
        assertEquals(2, g.leafCount)
        val (left, right) = g.children.filterIsInstance<io.github.nd2026.edited.ui.editor.EditorLeafWidget>()
        assertTrue(left.bounds.right() <= right.bounds.x - EditorGroupWidget.SPLITTER + 1)
        assertEquals(left.bounds.height, right.bounds.height)
        assertEquals(left.activeDocument, right.activeDocument)
    }

    @Test
    fun splitDownStacksPanes() {
        val g = group()
        g.split(Axis.VERTICAL)
        val (top, bottom) = g.children.filterIsInstance<io.github.nd2026.edited.ui.editor.EditorLeafWidget>()
        assertTrue(top.bounds.bottom() <= bottom.bounds.y)
        assertEquals(top.bounds.width, bottom.bounds.width)
    }

    @Test
    fun nestedSplitsTileTheWholeArea() {
        val g = group()
        g.split(Axis.HORIZONTAL)
        g.split(Axis.VERTICAL)
        assertEquals(3, g.leafCount)
        val area = g.children.filterIsInstance<io.github.nd2026.edited.ui.editor.EditorLeafWidget>()
            .sumOf { it.bounds.width.toLong() * it.bounds.height }
        // Splitters take a few pixels; the panes must cover nearly all of the group and never overlap it.
        assertTrue(area <= 1000L * 600 && area > 1000L * 600 * 0.97, "area=$area")
    }

    @Test
    fun draggingTheSplitterMovesTheDivider() {
        val g = group()
        g.split(Axis.HORIZONTAL)
        val leaves = g.children.filterIsInstance<io.github.nd2026.edited.ui.editor.EditorLeafWidget>()
        val before = leaves[0].bounds.width
        val gapX = leaves[0].bounds.right() + 1
        assertTrue(g.onMouseEvent(InputEvent.MousePressed(gapX, 300, java.awt.event.MouseEvent.BUTTON1, 0)))
        g.onMouseEvent(InputEvent.MouseDragged(gapX + 150, 300, 0))
        g.onMouseEvent(InputEvent.MouseReleased(gapX + 150, 300, java.awt.event.MouseEvent.BUTTON1, 0))
        assertTrue(leaves[0].bounds.width > before + 100, "before=$before after=${leaves[0].bounds.width}")
    }

    @Test
    fun splitterNeverShrinksAPaneBelowItsMinimum() {
        val g = group()
        g.split(Axis.HORIZONTAL)
        val leaves = g.children.filterIsInstance<io.github.nd2026.edited.ui.editor.EditorLeafWidget>()
        val gapX = leaves[0].bounds.right() + 1
        g.onMouseEvent(InputEvent.MousePressed(gapX, 300, java.awt.event.MouseEvent.BUTTON1, 0))
        g.onMouseEvent(InputEvent.MouseDragged(-5000, 300, 0))
        assertTrue(leaves[0].bounds.width >= EditorGroupWidget.MIN_PANE)
        g.onMouseEvent(InputEvent.MouseDragged(9000, 300, 0))
        assertTrue(leaves[1].bounds.width >= EditorGroupWidget.MIN_PANE)
    }

    @Test
    fun closingAPaneLetsTheSiblingTakeOverAndTheLastPaneCannotClose() {
        val g = group()
        g.split(Axis.HORIZONTAL)
        assertTrue(g.closeActivePane())
        assertEquals(1, g.leafCount)
        val only = g.children.filterIsInstance<io.github.nd2026.edited.ui.editor.EditorLeafWidget>().single()
        assertEquals(1000, only.bounds.width)
        assertEquals(false, g.closeActivePane())
    }

    @Test
    fun splitViewsShareTheDocumentSoEditsAppearInBoth() {
        val g = group()
        g.split(Axis.HORIZONTAL)
        val leaves = g.children.filterIsInstance<io.github.nd2026.edited.ui.editor.EditorLeafWidget>()
        val a = leaves[0].activeDocument!!.textArea
        val b = leaves[1].activeDocument!!.textArea
        a.insert(0, "추가")
        assertEquals(a.snapshot(), b.snapshot())
        assertTrue(b.snapshot().startsWith("추가"))
    }
}

class EditorPaneWidgetTest {
    @Test
    fun gutterWidensWhenLineCountGainsADigit() {
        val doc = EditorDocument("a", "a.md", TextArea("x\n".repeat(98)))
        val pane = EditorPaneWidget(doc, NoAnalyzer)
        pane.setBounds(0, 0, 800, 400)
        pane.onAttach()
        val before = pane.editor.bounds.x
        doc.textArea.insert(doc.textArea.length, "x\n".repeat(2000))
        assertTrue(pane.editor.bounds.x > before, "gutter should grow for 4-digit line numbers")
        pane.onDetach()
    }

    @Test
    fun panePaintsGutterBreadcrumbAndStripeWithoutErrors() {
        val doc = EditorDocument("a", "a.md", TextArea("# 작품\n## 제1화\n본문\n***\n다음 장면\n"))
        doc.inspections.set(listOf(Inspection(2, Severity.ERROR, "e"), Inspection(4, Severity.WARNING, "w")))
        val pane = EditorPaneWidget(doc, NoAnalyzer)
        pane.setBounds(0, 0, 800, 400)
        pane.onAttach()
        doc.textArea.moveCaretTo(doc.textArea.length - 1)
        val image = BufferedImage(800, 400, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        pane.paint(g, Rectangle(0, 0, 800, 400))
        g.dispose()
        // Something other than the blank background must have been drawn in the stripe column.
        val stripeX = 800 - EditorPaneWidget.STRIPE_WIDTH + 5
        val colors = (0 until 400).map { image.getRGB(stripeX, it) }.toSet()
        assertNotEquals(1, colors.size)
        pane.onDetach()
    }
}

private fun Rectangle.right() = x + width
private fun Rectangle.bottom() = y + height
