package io.github.nd2026.edited.perf

import io.github.nd2026.edited.core.GapBufferTextBuffer
import io.github.nd2026.edited.core.TextArea
import io.github.nd2026.edited.core.TextBuffer
import io.github.nd2026.edited.ui.editor.EditorDocument
import io.github.nd2026.edited.ui.editor.EditorPaneWidget
import java.awt.Rectangle
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Regression budgets for the editor on a 1,000,000-character manuscript. Measured values are ~10x
 * below these (see docs/EDITOR_PERFORMANCE.md), so the budgets only trip on a real regression such as
 * an O(document) step creeping into painting or typing, not on a slow CI machine.
 * The 10M-character numbers come from `./gradlew :app:editorBenchmark`.
 */
class EditorPerformanceTest {
    private companion object {
        const val FRAME_P95_MS = 33.0 // 30 fps
        const val FRAME_P99_MS = 100.0
        const val ALLOCATION_KB_PER_FRAME = 1024.0
    }

    @Test
    fun oneMillionCharsStaysWithinFrameAndInputBudgets() {
        val r = EditorPerf.run(1_000_000, typingStrokes = 150, scrollFrames = 150)
        println(EditorPerf.markdown(listOf(r)))
        for ((name, stats) in listOf(
            "scroll" to r.scrollFrame,
            "typing" to r.typingLatency,
            "typing after jump" to r.typingAfterJumpLatency,
            "undo" to r.undoStep,
        )) {
            assertTrue(stats.p95 <= FRAME_P95_MS, "$name p95 ${stats.p95} ms > $FRAME_P95_MS ms")
            assertTrue(stats.p99 <= FRAME_P99_MS, "$name p99 ${stats.p99} ms > $FRAME_P99_MS ms")
        }
        assertTrue(r.allocatedKilobytesPerFrame <= ALLOCATION_KB_PER_FRAME, "allocation ${r.allocatedKilobytesPerFrame} KB/frame")
    }

    /** Counts how much of the document a frame actually reads. */
    private class CountingBuffer(private val inner: TextBuffer) : TextBuffer by inner {
        var charsRead = 0L
        override fun charAt(index: Int): Char { charsRead++; return inner.charAt(index) }
        override fun subSequence(start: Int, end: Int): String { charsRead += end - start; return inner.subSequence(start, end) }
        fun reset() { charsRead = 0 }
    }

    @Test
    fun paintingAFrameReadsOnlyTheVisibleLinesNotTheDocument() {
        val text = EditorPerf.fixture(1_000_000)
        val counting = CountingBuffer(GapBufferTextBuffer(text))
        val pane = EditorPaneWidget(EditorDocument("d", "d.md", TextArea(counting)), EditorPerf.noAnalyzer)
        pane.setBounds(0, 0, EditorPerf.WIDTH, EditorPerf.HEIGHT)
        pane.onAttach()
        val image = BufferedImage(EditorPerf.WIDTH, EditorPerf.HEIGHT, BufferedImage.TYPE_INT_ARGB)
        pane.editor.scrollTo(y = pane.editor.lineHeight * 3_000)

        counting.reset()
        val g = image.createGraphics()
        pane.paint(g, Rectangle(0, 0, EditorPerf.WIDTH, EditorPerf.HEIGHT))
        g.dispose()

        // ~45 visible lines of a few hundred characters, plus the bounded breadcrumb scan: far below 1M.
        assertTrue(counting.charsRead < 150_000, "a frame read ${counting.charsRead} chars of a 1,000,000-char document")
        pane.onDetach()
    }

    @Test
    fun aKeystrokeDoesNotReadTheWholeDocument() {
        val counting = CountingBuffer(GapBufferTextBuffer(EditorPerf.fixture(1_000_000)))
        val area = TextArea(counting)
        val pane = EditorPaneWidget(EditorDocument("d", "d.md", area), EditorPerf.noAnalyzer)
        pane.setBounds(0, 0, EditorPerf.WIDTH, EditorPerf.HEIGHT)
        pane.onAttach()
        area.moveCaretTo(500_000)
        counting.reset()
        area.typeAtCaret("가", coalesce = true)
        assertTrue(counting.charsRead < 150_000, "a keystroke read ${counting.charsRead} chars")
        pane.onDetach()
    }
}
