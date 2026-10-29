package io.github.nd2026.edited.stats

/** A content word and how often it appears (by lemma: 먹었다/먹고 both count as 먹다). */
data class WordCount(val lemma: String, val partOfSpeech: String, val count: Int)

/** Morpheme counts of one text, for "how descriptive is this chapter" figures. */
data class PosRatio(val nouns: Int, val predicates: Int, val adverbs: Int, val total: Int) {
    /** Adverbs per 100 content words; 0 when there are none. */
    val adverbsPer100: Int get() = if (total == 0) 0 else adverbs * 100 / total
}

/** Word-level statistics built on `lexer/Morphology`. */
object WordStats {
    // TODO(작업 2-5): content words only (Morpheme.isContentWord), grouped by Morpheme.lemma, most frequent first.
    fun topWords(text: String, limit: Int = 10): List<WordCount> = emptyList()

    // TODO(작업 2-6)
    fun posRatio(text: String): PosRatio = PosRatio(0, 0, 0, 0)
}
