package io.github.nd2026.edited.lexer

/**
 * A single morpheme produced by a [MorphemeAnalyzer]. Offsets are document-absolute (not
 * relative to whatever chunk of text was analyzed) so results can be spliced straight into a
 * document-wide index without extra bookkeeping.
 */
data class Morpheme(
    val surface: String,
    val start: Int,
    val end: Int,
    val partOfSpeech: String,
    /** Sub-morphemes when [surface] is a compound the analyzer decomposed, empty otherwise. */
    val decompound: List<Morpheme> = emptyList(),
)
