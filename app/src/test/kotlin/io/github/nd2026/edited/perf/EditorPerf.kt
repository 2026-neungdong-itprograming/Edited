package io.github.nd2026.edited.perf

import io.github.nd2026.edited.core.GapBufferTextBuffer
import io.github.nd2026.edited.core.TextArea
import io.github.nd2026.edited.event.InputEvent
import io.github.nd2026.edited.lexer.Morpheme
import io.github.nd2026.edited.lexer.MorphemeAnalyzer
import io.github.nd2026.edited.ui.editor.EditorDocument
import io.github.nd2026.edited.ui.editor.EditorPaneWidget
import java.awt.Rectangle
import java.awt.event.KeyEvent
import java.awt.image.BufferedImage
import java.lang.management.ManagementFactory
import kotlin.random.Random

/** Millisecond distribution of one measured operation. */
data class Stats(val samples: Int, val p50: Double, val p95: Double, val p99: Double, val max: Double) {
    override fun toString() = "p50 %.2f / p95 %.2f / p99 %.2f / max %.2f ms (n=%d)".format(p50, p95, p99, max, samples)

    companion object {
        fun of(nanos: LongArray): Stats {
            val sorted = nanos.sorted()
            fun at(q: Double) = sorted[((sorted.size - 1) * q).toInt()] / 1e6
            return Stats(sorted.size, at(0.50), at(0.95), at(0.99), sorted.last() / 1e6)
        }
    }
}

data class PerfResult(
    val chars: Int,
    val lines: Int,
    val loadMillis: Double,
    val heapMegabytes: Double,
    val scrollFrame: Stats,
    val typingLatency: Stats,
    val typingAfterJumpLatency: Stats,
    val undoStep: Stats,
    val allocatedKilobytesPerFrame: Double,
)

/**
 * Measures the editor shell headlessly on a synthetic manuscript: real [EditorPaneWidget] (breadcrumb,
 * gutter, stripe, text) painted into a [BufferedImage] the size of a normal window.
 *
 * "Input latency" is key event -> that frame fully painted, so it includes the text edit, every
 * listener (line index, inspection model, caret reveal) and the paint - everything the author waits for
 * except the OS delivering the event and the screen scanning out the buffer.
 */
object EditorPerf {
    const val WIDTH = 1200
    const val HEIGHT = 800

    internal val noAnalyzer: MorphemeAnalyzer = object : MorphemeAnalyzer {
        override fun analyze(text: String, baseOffset: Int): List<Morpheme> = emptyList()
    }

    /** Paragraph-per-line prose with chapter headings, dialogue and scene breaks, `chars` characters long. */
    fun fixture(chars: Int, seed: Int = 1): String {
        val random = Random(seed)
        val words = listOf("서진은", "열쇠를", "조용히", "바라보았다.", "비가", "그친", "뒤의", "골목은", "유난히", "고요했다.", "“기다리고", "있었습니다.”", "남자의", "손끝이", "떨렸다.", "북부", "관문은", "폐쇄되었다.")
        val out = StringBuilder(chars + 400)
        var chapter = 1
        while (out.length < chars) {
            if (out.length > 0 && random.nextInt(400) == 0) out.append("# 제").append(++chapter).append("부\n")
            when (random.nextInt(60)) {
                0 -> out.append("## 제").append(++chapter).append("화. 다음 이야기\n")
                1 -> out.append("***\n")
                else -> {
                    repeat(random.nextInt(8, 60)) { out.append(words[random.nextInt(words.size)]).append(' ') }
                    out.append('\n')
                }
            }
        }
        out.setLength(chars)
        return out.toString()
    }

    fun run(chars: Int, typingStrokes: Int = 300, scrollFrames: Int = 300): PerfResult {
        System.gc()
        val heapBefore = usedHeap()
        val text = fixture(chars)
        val loadStart = System.nanoTime()
        val area = TextArea(GapBufferTextBuffer(text))
        val loadMillis = (System.nanoTime() - loadStart) / 1e6
        val document = EditorDocument("fixture", "fixture.md", area)
        val pane = EditorPaneWidget(document, noAnalyzer)
        pane.setBounds(0, 0, WIDTH, HEIGHT)
        pane.onAttach()
        System.gc()
        val heapMegabytes = (usedHeap() - heapBefore) / 1e6

        val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
        fun frame() {
            val g = image.createGraphics()
            try {
                pane.paint(g, Rectangle(0, 0, WIDTH, HEIGHT))
            } finally {
                g.dispose()
            }
        }
        repeat(30) { frame() } // JIT warm-up

        val random = Random(99)
        val editor = pane.editor
        val maxScroll = (area.lineCount * editor.lineHeight - editor.bounds.height).coerceAtLeast(1)

        val allocation = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val thread = Thread.currentThread().threadId()
        val allocStart = allocation.getThreadAllocatedBytes(thread)
        val scroll = LongArray(scrollFrames) {
            val start = System.nanoTime()
            editor.scrollTo(y = random.nextInt(maxScroll))
            frame()
            System.nanoTime() - start
        }
        val allocatedPerFrame = (allocation.getThreadAllocatedBytes(thread) - allocStart) / scrollFrames / 1024.0

        // Typing in place: the caret sits mid-document, one character per frame.
        area.moveCaretTo(area.length / 2)
        frame()
        val typing = LongArray(typingStrokes) { i ->
            val start = System.nanoTime()
            editor.onKeyEvent(InputEvent.KeyTyped("가나다라마바사"[i % 7], 0))
            frame()
            System.nanoTime() - start
        }

        // Worst case for a gap buffer: every keystroke lands far from the previous one, moving the gap.
        val afterJump = LongArray(typingStrokes) {
            area.moveCaretTo(random.nextInt(area.length))
            val start = System.nanoTime()
            editor.onKeyEvent(InputEvent.KeyTyped('x', 0))
            frame()
            System.nanoTime() - start
        }

        val undo = LongArray(typingStrokes) {
            val start = System.nanoTime()
            area.undo()
            frame()
            System.nanoTime() - start
        }

        pane.onDetach()
        area.close()
        return PerfResult(
            chars, area.lineCount, loadMillis, heapMegabytes,
            Stats.of(scroll), Stats.of(typing), Stats.of(afterJump), Stats.of(undo), allocatedPerFrame,
        )
    }

    private fun usedHeap(): Long = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }

    fun markdown(results: List<PerfResult>): String = buildString {
        appendLine("# Editor performance")
        appendLine()
        appendLine("Headless; ${WIDTH}x$HEIGHT frame; `EditorPaneWidget` (breadcrumb + gutter + text + stripe). JDK ${System.getProperty("java.version")}, ${Runtime.getRuntime().availableProcessors()} CPUs.")
        for (r in results) {
            appendLine()
            appendLine("## %,d chars (%,d lines)".format(r.chars, r.lines))
            appendLine()
            appendLine("| metric | result |")
            appendLine("| --- | --- |")
            appendLine("| load (gap buffer + line index) | %.0f ms |".format(r.loadMillis))
            appendLine("| heap retained | %.1f MB |".format(r.heapMegabytes))
            appendLine("| scroll frame (random jump) | ${r.scrollFrame} |")
            appendLine("| type in place, key -> frame | ${r.typingLatency} |")
            appendLine("| type after caret jump, key -> frame | ${r.typingAfterJumpLatency} |")
            appendLine("| undo -> frame | ${r.undoStep} |")
            appendLine("| allocation per scroll frame | %.0f KB |".format(r.allocatedKilobytesPerFrame))
        }
    }
}

/** `./gradlew :app:editorBenchmark` entry point. */
fun main(args: Array<String>) {
    System.setProperty("java.awt.headless", "true")
    val sizes = args.map { it.toInt() }.ifEmpty { listOf(1_000_000, 10_000_000) }
    val report = EditorPerf.markdown(sizes.map { EditorPerf.run(it) })
    println(report)
    java.io.File("build/reports").apply { mkdirs() }.resolve("editor-perf.md").writeText(report)
}
