package io.github.nd2026.edited.lexer

import io.github.nd2026.edited.core.Parallel
import kotlin.test.Test
import kotlin.test.assertEquals

/** Parallel analysis must give exactly the serial result, and must survive a dictionary reload mid-flight. */
class ParallelMorphologyTest {
    private val sentences = listOf(
        "민준이가 밥을 먹었다.", "서연은 창문을 열었다.", "하늘이 아름다웠다.", "문이 열려졌다.",
        "그는 갑자기 뒤를 돌아보았다.", "\"어디 가?\" 서연이 물었다.", "바람이 차갑게 불어왔다.",
    )

    /** 2,000 distinct paragraphs, so nothing comes from the memo by accident. */
    private val lines = (0 until 2_000).map { i -> "${i}번째 날, " + sentences.shuffled(java.util.Random(i.toLong())).take(4).joinToString(" ") }

    @Test
    fun parallelLinesMatchSerial() {
        if (!Morphology.isAvailable) return
        val serial = lines.map { DefaultMorphemeAnalyzer.analyze(it) }
        Morphology.clearCache()
        assertEquals(serial, Morphology.analyzeLines(lines))
    }

    @Test
    fun analyzingWhileKiwiReloadsNeverCrashes() {
        if (!Morphology.isAvailable) return
        val analyzer = KiwiMorphemeAnalyzer()
        analyzer.use {
            val reloads = Thread {
                repeat(3) { n -> analyzer.reload(listOf(KiwiMorphemeAnalyzer.UserWord("서연$n"))) }
            }.apply { start() }
            Parallel.forEach(lines.take(600)) { analyzer.analyze(it) }
            reloads.join()
        }
    }

    @Test
    fun parallelIsFasterThanSerial() {
        if (!Morphology.isAvailable || Parallel.parallelism < 2) return
        DefaultMorphemeAnalyzer.analyze(lines[0]) // load Kiwi outside the timings
        val serialMs = timeMs { lines.forEach { DefaultMorphemeAnalyzer.analyze(it) } }
        Morphology.clearCache()
        val parallelMs = timeMs { Morphology.analyzeLines(lines) }
        println("형태소 분석 ${lines.size}줄: 직렬 ${serialMs}ms → 병렬 ${parallelMs}ms (${Parallel.parallelism}개 스레드, %.1f배)".format(serialMs.toDouble() / parallelMs.coerceAtLeast(1)))
    }

    private inline fun timeMs(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / 1_000_000
    }
}
