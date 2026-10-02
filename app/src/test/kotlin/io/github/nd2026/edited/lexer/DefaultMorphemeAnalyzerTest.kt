package io.github.nd2026.edited.lexer

import kotlin.test.Test
import kotlin.test.assertTrue

class DefaultMorphemeAnalyzerTest {
    @Test
    fun analyzesKoreanWithOriginalOffsets() {
        val result = DefaultMorphemeAnalyzer.analyze("윤서진은 문을 밀었다.", baseOffset = 10)
        assertTrue(result.isNotEmpty(), "Kiwi produced no morphemes (model missing?)")
        assertTrue(result.all { it.start >= 10 })
        assertTrue(result.any { it.partOfSpeech.startsWith("J") }, "expected a particle: $result")
    }
}
