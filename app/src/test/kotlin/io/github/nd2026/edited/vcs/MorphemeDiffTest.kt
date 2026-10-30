package io.github.nd2026.edited.vcs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 작업 6-6, 6-8. */
class MorphemeDiffTest {
    @Test
    fun endingOnlyChange() {
        assertEquals(WordChangeKind.ENDING, MorphemeDiff.classify("그는 밥을 먹었다.", "그는 밥을 먹는다."))
        assertTrue(MorphemeDiff.changedWords("그는 밥을 먹었다.", "그는 밥을 먹는다.").isEmpty())
    }

    @Test
    fun particleOnlyChange() {
        assertEquals(WordChangeKind.PARTICLE, MorphemeDiff.classify("민준이 웃었다.", "민준은 웃었다."))
        assertTrue(MorphemeDiff.changedWords("민준이 웃었다.", "민준은 웃었다.").isEmpty())
    }

    @Test
    fun wordChange() {
        assertEquals(WordChangeKind.WORDS, MorphemeDiff.classify("그는 문을 열었다.", "그는 창문을 열었다."))
        assertEquals(listOf<Pair<String?, String?>>("문" to "창문"), MorphemeDiff.changedWords("그는 문을 열었다.", "그는 창문을 열었다."))
        // A verb is reported by its dictionary form.
        assertEquals(listOf<Pair<String?, String?>>("열다" to "닫다"), MorphemeDiff.changedWords("그는 문을 열었다.", "그는 문을 닫았다."))
    }

    @Test
    fun addedAndRemovedWords() {
        assertEquals(WordChangeKind.WORDS, MorphemeDiff.classify("그는 웃었다.", "그는 활짝 웃었다."))
        assertEquals(listOf<Pair<String?, String?>>(null to "활짝"), MorphemeDiff.changedWords("그는 웃었다.", "그는 활짝 웃었다."))
        assertEquals(listOf<Pair<String?, String?>>("활짝" to null), MorphemeDiff.changedWords("그는 활짝 웃었다.", "그는 웃었다."))
    }

    @Test
    fun mixedAndUnchangedFallBackToWords() {
        // A particle and an ending changed together: not "only" either one.
        assertEquals(WordChangeKind.WORDS, MorphemeDiff.classify("민준이 웃었다.", "민준은 웃는다."))
        // Only the spacing differs: same morphemes, nothing to call out.
        assertEquals(WordChangeKind.WORDS, MorphemeDiff.classify("그는 문을 열었다.", "그는 문을열었다."))
        assertTrue(MorphemeDiff.changedWords("그는 웃었다.", "그는 웃었다.").isEmpty())
        assertTrue(MorphemeDiff.changedWords("", "").isEmpty())
    }
}
