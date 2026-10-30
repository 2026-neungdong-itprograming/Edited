package io.github.nd2026.edited.vcs

import io.github.nd2026.edited.lexer.Morpheme
import io.github.nd2026.edited.lexer.Morphology
import io.github.nd2026.edited.lexer.isContentWord
import io.github.nd2026.edited.lexer.isEnding
import io.github.nd2026.edited.lexer.isParticle
import io.github.nd2026.edited.lexer.lemma

/** What kind of edit turned one sentence into another. */
enum class WordChangeKind {
    /** Only endings changed: "먹었다" → "먹는다". */
    ENDING,

    /** Only particles changed: "민준이" → "민준은". */
    PARTICLE,

    /** Content words were added, removed or replaced. */
    WORDS,
}

/**
 * Morpheme-level view of a [SentenceChange.Kind.CHANGED] pair. The two sentences' morphemes are lined up (longest
 * common subsequence of surface + tag); what is left over on either side is "what changed". Without Kiwi there are
 * no morphemes: [classify] answers [WordChangeKind.WORDS] and [changedWords] is empty.
 */
object MorphemeDiff {
    /** The morphemes that differ in one stretch between two common morphemes. */
    private class Gap(val removed: List<Morpheme>, val added: List<Morpheme>)

    /**
     * ENDING when every difference between [before] and [after] is an ending (`E*`), PARTICLE when every one is a
     * particle (`J*`, or the name suffix `이` that sits where a particle would: "민준이"), WORDS otherwise. Punctuation
     * is ignored; identical morphemes (only spacing changed) or punctuation-only changes count as WORDS.
     */
    fun classify(before: String, after: String): WordChangeKind {
        val different = gaps(before, after).flatMap { it.removed + it.added }.filterNot { it.partOfSpeech.startsWith("S") }
        return when {
            different.isEmpty() -> WordChangeKind.WORDS
            different.all { it.isEnding } -> WordChangeKind.ENDING
            different.all { it.isParticle || (it.partOfSpeech == "XSN" && it.surface == "이") } -> WordChangeKind.PARTICLE
            else -> WordChangeKind.WORDS
        }
    }

    /**
     * The content words that differ, as (before, after) in dictionary form: "문을 열었다" → "창문을 열었다" gives
     * `문 → 창문`. Within one stretch of difference the words are paired in order; the unpaired side is null
     * (a word that was added or removed).
     */
    fun changedWords(before: String, after: String): List<Pair<String?, String?>> = gaps(before, after).flatMap { gap ->
        val removed = gap.removed.filter { it.isContentWord }.map { it.lemma }
        val added = gap.added.filter { it.isContentWord }.map { it.lemma }
        List(maxOf(removed.size, added.size)) { removed.getOrNull(it) to added.getOrNull(it) }
    }

    private fun gaps(before: String, after: String): List<Gap> {
        val a = Morphology.analyze(before)
        val b = Morphology.analyze(after)
        if (a.isEmpty() && b.isEmpty()) return emptyList()

        // lcs[i][j] = length of the longest common run of morphemes in a[i..] and b[j..].
        val lcs = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.indices.reversed()) for (j in b.indices.reversed()) {
            lcs[i][j] = if (same(a[i], b[j])) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
        }
        val result = ArrayList<Gap>()
        var removed = ArrayList<Morpheme>()
        var added = ArrayList<Morpheme>()
        fun flush() {
            if (removed.isNotEmpty() || added.isNotEmpty()) result += Gap(removed, added)
            removed = ArrayList()
            added = ArrayList()
        }
        var i = 0
        var j = 0
        while (i < a.size || j < b.size) {
            when {
                i < a.size && j < b.size && same(a[i], b[j]) -> { flush(); i++; j++ }
                j < b.size && (i == a.size || lcs[i][j + 1] >= lcs[i + 1][j]) -> added += b[j++]
                else -> removed += a[i++]
            }
        }
        flush()
        return result
    }

    private fun same(x: Morpheme, y: Morpheme) = x.surface == y.surface && x.partOfSpeech == y.partOfSpeech
}
