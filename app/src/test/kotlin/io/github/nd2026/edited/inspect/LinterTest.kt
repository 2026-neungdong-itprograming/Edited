package io.github.nd2026.edited.inspect

import io.github.nd2026.edited.core.Sentences
import io.github.nd2026.edited.core.Severity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LinterTest {
    @Test
    fun splitsSentencesKeepingClosingQuotes() {
        assertEquals(listOf("\"가자.\"", "그가 말했다!?", "끝"), Sentences.split("\"가자.\" 그가 말했다!? 끝"))
        assertEquals(listOf("첫 줄", "둘째 줄…"), Sentences.split("첫 줄\n\n둘째 줄…"))
    }

    @Test
    fun longSentence() {
        val rule = LongSentenceRule(maxChars = 10)
        assertEquals(listOf(1), rule.check(listOf("짧다.", "이 문장은 열 글자를 훨씬 넘어간다.")).map { it.line })
        assertTrue(rule.check(listOf("짧다. 이것도 짧다.")).isEmpty())
        assertEquals(Severity.WARNING, rule.check(listOf("가".repeat(11))).single().severity)
    }

    @Test
    fun endingRepetitionAcrossWebNovelParagraphs() {
        val rule = EndingRepetitionRule()
        val lines = listOf("문을 열었다.", "", "밥을 먹었다.", "", "그는 웃었다.")
        val found = rule.check(lines)
        assertEquals(listOf(4), found.map { it.line })
        assertEquals(Severity.INFO, found.single().severity)
        assertTrue(rule.check(listOf("문을 열었다.", "방은 어두웠다.", "그는 웃는다.")).isEmpty())
    }

    @Test
    fun endingRepetitionReportsOncePerRun() {
        val lines = listOf("가였다.", "나였다.", "다였다.", "라였다.", "마였다.")
        assertEquals(1, EndingRepetitionRule().check(lines).size)
    }

    @Test
    fun quotePairs() {
        val rule = QuotePairRule()
        assertEquals(listOf(0, 2), rule.check(listOf("\"어디 가?", "“괜찮아.”", "(속으로 생각했다.")).map { it.line })
        assertTrue(rule.check(listOf("\"응.\" 그가 답했다.", "‘설마’ 싶었다.")).isEmpty())
        assertEquals(Severity.ERROR, rule.check(listOf("“열기만")).single().severity)
    }

    @Test
    fun habitWords() {
        val rule = HabitWordRule()
        assertEquals(1, rule.check(listOf("갑자기 비가 왔다. 갑자기 바람이 불었다. 갑자기 해가 떴다.")).size)
        assertTrue(rule.check(listOf("갑자기 비가 왔다.", "갑자기 바람이 불었다.", "갑자기 해가 떴다.")).isEmpty())
    }

    @Test
    fun linterRunsAllRulesOnWindowsLineEndings() {
        val found = Linter.run("“열린 따옴표\r\n정상 문장.\r\n")
        assertEquals(listOf(0), found.map { it.line })
    }
}
