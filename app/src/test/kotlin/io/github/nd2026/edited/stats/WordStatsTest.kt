package io.github.nd2026.edited.stats

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 작업 2-5, 2-6: empty text, dialogue-only text and one verb in many forms. */
class WordStatsTest {
    @Test
    fun inflectedFormsCountAsOneWord() {
        val top = WordStats.topWords("밥을 먹었다. 빵을 먹고 물을 마셨다. 국을 먹는다.")
        assertEquals(WordCount("먹다", "VV", 3), top.first())
        // Equal counts are ordered alphabetically and every entry is a content word.
        assertEquals(listOf("먹다", "국", "마시다", "물", "밥", "빵"), top.map { it.lemma })
        assertTrue(top.none { it.partOfSpeech.startsWith("J") || it.partOfSpeech.startsWith("E") })
    }

    @Test
    fun limitAndLightVerbs() {
        val text = "그는 밥을 먹었다. 그는 일을 했다. 그는 집에 있었다. 밥이 맛있었다."
        assertEquals(1, WordStats.topWords(text, 1).size)
        assertTrue(WordStats.topWords(text).none { it.lemma in setOf("하다", "있다", "되다") })
        assertEquals("밥", WordStats.topWords(text).first().lemma)
        assertTrue(WordStats.topWords(text, 0).isEmpty())
    }

    @Test
    fun emptyAndDialogueOnlyText() {
        assertTrue(WordStats.topWords("").isEmpty())
        assertTrue(WordStats.topWords("\n\n  \n").isEmpty())
        assertEquals(PosRatio(0, 0, 0, 0), WordStats.posRatio(""))
        assertEquals(0, WordStats.posRatio("").adverbsPer100)
        // Only dialogue: quotes and marks are not words, the words inside are.
        val dialogue = "\"어디 가?\"\n\"집에 가. 너는?\"\n\"나도 집에.\""
        val top = WordStats.topWords(dialogue)
        assertTrue(top.isNotEmpty())
        assertTrue(top.any { it.lemma == "집" && it.count == 2 }, "$top")
    }

    @Test
    fun directivesAreNotWords() {
        val top = WordStats.topWords("@Suppress(\"맞춤법\")\n밥을 먹었다. @메모(\"나중에\") 빵을 먹었다.")
        assertEquals("먹다", top.first().lemma)
        assertTrue(top.none { it.lemma.contains("Suppress") || it.lemma == "메모" || it.lemma == "맞춤법" })
    }

    @Test
    fun partOfSpeechRatio() {
        val ratio = WordStats.posRatio("갑자기 매우 빨리 달렸다.")
        assertEquals(3, ratio.adverbs)
        assertEquals(1, ratio.predicates)
        assertEquals(0, ratio.nouns)
        assertEquals(4, ratio.total)
        assertEquals(75, ratio.adverbsPer100)

        val plain = WordStats.posRatio("서연은 창문을 열었다.")
        assertEquals(0, plain.adverbs)
        assertEquals(0, plain.adverbsPer100)
        assertEquals(2, plain.nouns) // 서연, 창문
        assertEquals(1, plain.predicates)
    }
}
