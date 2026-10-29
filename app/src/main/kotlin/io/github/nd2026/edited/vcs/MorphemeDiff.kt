package io.github.nd2026.edited.vcs

/** What kind of edit turned one sentence into another. */
enum class WordChangeKind {
    /** Only endings changed: "먹었다" → "먹는다". */
    ENDING,

    /** Only particles changed: "민준이" → "민준은". */
    PARTICLE,

    /** Content words were added, removed or replaced. */
    WORDS,
}

/** Morpheme-level view of a [SentenceChange.Kind.CHANGED] pair. */
object MorphemeDiff {
    // TODO(작업 6-6): compare the two sentences' morphemes; ENDING/PARTICLE when every difference is an E*/J* tag.
    fun classify(before: String, after: String): WordChangeKind = WordChangeKind.WORDS

    // TODO(작업 6-8): the differing content words, before → after (null when added/removed).
    fun changedWords(before: String, after: String): List<Pair<String?, String?>> = emptyList()
}
