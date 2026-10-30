package io.github.nd2026.edited.stats

import io.github.nd2026.edited.core.Directives
import io.github.nd2026.edited.lexer.CommonWords
import io.github.nd2026.edited.lexer.Morpheme
import io.github.nd2026.edited.lexer.Morphology
import io.github.nd2026.edited.lexer.isAdverb
import io.github.nd2026.edited.lexer.isContentWord
import io.github.nd2026.edited.lexer.isNoun
import io.github.nd2026.edited.lexer.isPredicate
import io.github.nd2026.edited.lexer.lemma

/** A content word and how often it appears (by lemma: 먹었다/먹고 both count as 먹다). */
data class WordCount(val lemma: String, val partOfSpeech: String, val count: Int)

/**
 * Morpheme counts of one text, for "how descriptive is this chapter" figures. [total] is the number of
 * words with a meaning of their own (nouns, verbs, adjectives and adverbs), the base of [adverbsPer100].
 */
data class PosRatio(val nouns: Int, val predicates: Int, val adverbs: Int, val total: Int) {
    /** Adverbs per 100 content words; 0 when there are none. */
    val adverbsPer100: Int get() = if (total == 0) 0 else adverbs * 100 / total
}

/** Word-level statistics built on `lexer/Morphology`. */
object WordStats {
    /**
     * The [limit] most used content words ([isContentWord]) grouped by dictionary form, most used first (ties
     * alphabetically): "먹었다 먹고" is `먹다` twice. Everyday light verbs ([CommonWords.LIGHT_PREDICATES]: 하다,
     * 있다, 되다 …) are left out, since they top every list. Empty without Kiwi.
     */
    fun topWords(text: String, limit: Int = 10): List<WordCount> {
        if (limit <= 0) return emptyList()
        val counts = HashMap<Pair<String, String>, Int>()
        for (m in morphemesOf(text)) {
            if (!m.isContentWord || m.lemma in CommonWords.LIGHT_PREDICATES) continue
            counts.merge(m.lemma to m.partOfSpeech, 1, Int::plus)
        }
        return counts.map { (key, count) -> WordCount(key.first, key.second, count) }
            .sortedWith(compareByDescending<WordCount> { it.count }.thenBy { it.lemma }.thenBy { it.partOfSpeech })
            .take(limit)
    }

    /** Nouns, verbs/adjectives and adverbs in [text]; [PosRatio.total] counts the words that carry meaning. Zeros without Kiwi. */
    fun posRatio(text: String): PosRatio {
        var nouns = 0
        var predicates = 0
        var adverbs = 0
        var total = 0
        for (m in morphemesOf(text)) {
            if (m.isNoun) nouns++
            if (m.isPredicate) predicates++
            if (m.isAdverb) adverbs++
            if (m.isContentWord || m.isAdverb) total++
        }
        return PosRatio(nouns, predicates, adverbs, total)
    }

    /** Every morpheme of the prose in [text]; directives (`@Suppress …`) are notes, not prose. */
    private fun morphemesOf(text: String): List<Morpheme> {
        if (text.isBlank() || !Morphology.isAvailable) return emptyList()
        return Morphology.analyzeLines(Directives.strip(text).split('\n').map { it.removeSuffix("\r") }).flatten()
    }
}
