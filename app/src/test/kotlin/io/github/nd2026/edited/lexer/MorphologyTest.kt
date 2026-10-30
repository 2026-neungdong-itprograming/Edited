package io.github.nd2026.edited.lexer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Shows what `Morphology` returns; the 2차 tasks build on exactly these shapes. */
class MorphologyTest {
    @Test
    fun predicatesComeBackAsStemsAndLemmaAddsDa() {
        val morphemes = Morphology.analyze("민준이가 밥을 먹었다.")
        assertEquals(listOf("민준", "밥", "먹다"), morphemes.filter { it.isContentWord }.map { it.lemma })
        assertEquals(listOf("가", "을"), morphemes.filter { it.isParticle }.map { it.surface })
        assertEquals(listOf("었", "다"), morphemes.filter { it.isEnding }.map { it.surface })
    }

    @Test
    fun analyzeLinesKeepsOffsetsPerLine() {
        val lines = listOf("서연은 웃었다.", "", "하늘이 맑았다.")
        val morphemes = Morphology.analyzeLines(lines)
        assertEquals(3, morphemes.size)
        assertTrue(morphemes[1].isEmpty())
        val first = morphemes[2].first()
        assertEquals("하늘", lines[2].substring(first.start, first.end))
    }

    @Test
    fun repeatedTextComesFromTheMemoAndAnalyzerShiftsOffsets() {
        val text = "그는 문을 열었다."
        val first = Morphology.analyze(text)
        assertTrue(first === Morphology.analyze(text) || !Morphology.isAvailable)
        val shifted = Morphology.analyzer.analyze(text, baseOffset = 10)
        assertEquals(first.map { it.start + 10 }, shifted.map { it.start })
    }
}
