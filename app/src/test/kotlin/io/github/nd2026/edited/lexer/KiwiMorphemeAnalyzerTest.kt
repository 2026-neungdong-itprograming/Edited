package io.github.nd2026.edited.lexer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KiwiMorphemeAnalyzerTest {

    @Test
    fun `offsets index the source text and tags are named`() {
        KiwiMorphemeAnalyzer().use { analyzer ->
            val text = "비가 그친 뒤의 골목은 유난히 조용했다."
            val morphemes = analyzer.analyze(text)

            assertTrue(morphemes.isNotEmpty())
            for (m in morphemes) {
                assertTrue(m.start in 0 until m.end && m.end <= text.length, "span out of range for $m")
                assertTrue(m.partOfSpeech != "UNKNOWN", "unnamed tag for $m")
            }
            // Uncontracted morphemes' surface is exactly their source span.
            val gol = morphemes.single { it.surface == "골목" }
            assertEquals("골목", text.substring(gol.start, gol.end))
            assertEquals("SF", morphemes.last().partOfSpeech)
            assertTrue(morphemes.any { it.partOfSpeech == "NNG" && it.surface == "골목" })
            assertTrue(morphemes.any { it.partOfSpeech.startsWith("J") })
            assertTrue(morphemes.any { it.partOfSpeech == "EF" })
        }
    }

    @Test
    fun `contracted syllable is split into morphemes sharing its span`() {
        KiwiMorphemeAnalyzer().use { analyzer ->
            val text = "했다"
            val morphemes = analyzer.analyze(text)
            val ha = morphemes.single { it.surface == "하" }
            val eot = morphemes.single { it.surface == "었" }
            assertEquals(0 to 1, ha.start to ha.end)
            assertEquals(0 to 1, eot.start to eot.end)
            assertEquals("EP", eot.partOfSpeech)
        }
    }

    @Test
    fun `baseOffset shifts every morpheme`() {
        KiwiMorphemeAnalyzer().use { analyzer ->
            val plain = analyzer.analyze("서진은 문을 밀었다.")
            val shifted = analyzer.analyze("서진은 문을 밀었다.", baseOffset = 100)
            assertEquals(plain.map { it.start + 100 }, shifted.map { it.start })
            assertEquals(plain.map { it.end + 100 }, shifted.map { it.end })
        }
    }

    @Test
    fun `empty text yields no morphemes`() {
        KiwiMorphemeAnalyzer().use { assertEquals(emptyList(), it.analyze("")) }
    }

    @Test
    fun `user word is kept whole as a proper noun and reload applies it`() {
        KiwiMorphemeAnalyzer().use { analyzer ->
            analyzer.reload(listOf(KiwiMorphemeAnalyzer.UserWord("윤서진느")))
            val morphemes = analyzer.analyze("윤서진느는 웃었다.")
            assertTrue(
                morphemes.any { it.surface == "윤서진느" && it.partOfSpeech == "NNP" },
                "expected 윤서진느/NNP in $morphemes",
            )
        }
    }
}
