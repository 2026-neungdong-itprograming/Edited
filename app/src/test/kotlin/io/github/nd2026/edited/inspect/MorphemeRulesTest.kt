package io.github.nd2026.edited.inspect

import io.github.nd2026.edited.core.Severity
import io.github.nd2026.edited.lexer.Morphology
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 작업 1-6, 1-7, 1-8: each rule has sentences it catches and sentences it leaves alone. */
class MorphemeRulesTest {
    private fun lines(text: String) = text.split('\n')
    private fun RepeatedWordRule.run(text: String) = lines(text).let { check(it, Morphology.analyzeLines(it)) }
    private fun PassiveVoiceRule.run(text: String) = lines(text).let { check(it, Morphology.analyzeLines(it)) }
    private fun EndingRepetitionRule.run(text: String) = lines(text).let { check(it, Morphology.analyzeLines(it)) }

    // --- 1-6 RepeatedWordRule

    @Test
    fun repeatedWordWithinThreeSentences() {
        val found = RepeatedWordRule().run("그는 문을 열었다. 문 밖은 추웠다.")
        assertEquals(listOf(0), found.map { it.line })
        assertEquals(Severity.INFO, found.single().severity)
        assertTrue("문" in found.single().message)
        // Conjugated forms are the same word: 열다 / 열고.
        assertEquals(1, RepeatedWordRule().run("그는 창을 열었다. 서둘러 문을 열고 나갔다.").size)
        // Also two sentences apart, but not four.
        assertEquals(1, RepeatedWordRule().run("바람이 불었다. 비가 왔다. 바람이 거세졌다.").size)
        assertTrue(RepeatedWordRule().run("바람이 불었다. 비가 왔다. 해가 졌다. 별이 떴다. 바람이 멎었다.").isEmpty())
    }

    @Test
    fun repeatedWordIgnoresParticlesNamesAndEverydayWords() {
        // Particles and endings repeat all the time: 는, 을, 었, 다.
        assertTrue(RepeatedWordRule().run("그는 밥을 먹었다. 나는 빵을 샀다.").isEmpty())
        // A name naturally comes back.
        assertTrue(RepeatedWordRule().run("서연은 웃었다. 서연은 울었다.").isEmpty())
        // 하다/있다 are light verbs.
        assertTrue(RepeatedWordRule().run("그는 일을 했다. 그녀도 일을 했다.").none { "하다" in it.message })
    }

    @Test
    fun repeatedWordStartsOverAtAParagraphBreak() {
        assertTrue(RepeatedWordRule().run("그는 문을 열었다.\n\n문 밖은 추웠다.").isEmpty())
        assertEquals(listOf(1), RepeatedWordRule().run("그는 문을 열었다.\n문 밖은 추웠다.").map { it.line })
    }

    // --- 1-7 EndingRepetitionRule on morphemes

    @Test
    fun endingRepetitionLooksAtTheEndingNotTheLastSyllables() {
        // 났다 / 갔다 / 었다 differ as syllables, but are all 었+다.
        val text = "그는 일어났다.\n그녀는 갔다.\n아이는 먹었다."
        assertEquals(listOf(2), EndingRepetitionRule().run(text).map { it.line })
        assertTrue(EndingRepetitionRule().check(lines(text)).isEmpty())
        val found = EndingRepetitionRule().run(text).single()
        assertEquals(Severity.INFO, found.severity)
        assertTrue("었다" in found.message)
    }

    @Test
    fun endingRepetitionNeedsThreeInARow() {
        assertTrue(EndingRepetitionRule().run("문을 열었다.\n방은 어두웠다.\n그는 웃는다.").isEmpty())
        assertTrue(EndingRepetitionRule().run("문을 열었다.\n그는 웃는다.\n방은 어두웠다.").isEmpty())
        // One report per run, however long.
        assertEquals(1, EndingRepetitionRule().run("가 왔다.\n나 갔다.\n다 섰다.\n라 앉았다.\n마 떠났다.").size)
        // Sentences of one line count one by one.
        assertEquals(1, EndingRepetitionRule().run("문을 열었다. 방은 어두웠다. 그는 웃었다.").size)
    }

    @Test
    fun linterRunsTheMorphemeRules() {
        val found = Linter.run("그는 일어났다.\n그녀는 갔다.\n아이는 먹었다.\n문이 열려졌다.")
        assertEquals(listOf(2, 3), found.map { it.line })
        assertTrue(Linter.rules.none { it is EndingRepetitionRule })
    }

    // --- 1-8 PassiveVoiceRule

    @Test
    fun doublePassiveIsAWarning() {
        for (sentence in listOf("문이 열려졌다.", "창이 닫혀졌다.", "별이 보여진다.", "글이 쓰여졌다.", "그는 결정되어졌다.", "그렇게 되어지다.")) {
            val found = PassiveVoiceRule().run(sentence)
            assertEquals(1, found.size, sentence)
            assertEquals(Severity.WARNING, found.single().severity, sentence)
        }
        assertTrue("열려졌다" in PassiveVoiceRule().run("문이 열려졌다.").single().message)
    }

    @Test
    fun singlePassiveAndActiveVerbsPass() {
        for (sentence in listOf("문이 열렸다.", "집이 만들어졌다.", "그날이 기다려졌다.", "얼굴이 예뻐졌다.", "소문이 믿어진다.", "일이 잘 되었다.", "그는 문을 열었다.")) {
            assertTrue(PassiveVoiceRule().run(sentence).isEmpty(), sentence)
        }
    }

    @Test
    fun doublePassiveIsReportedOnceEvenWhenBothChecksSeeIt() {
        // Kiwi reads 결정/되/어/지 as a verb chain and the text pattern sees 되어졌; still one warning.
        assertEquals(1, PassiveVoiceRule().run("계획이 결정되어졌다.").size)
        assertEquals(listOf(1), PassiveVoiceRule().run("평화로운 밤이었다.\n문이 열려졌다.").map { it.line })
    }
}
