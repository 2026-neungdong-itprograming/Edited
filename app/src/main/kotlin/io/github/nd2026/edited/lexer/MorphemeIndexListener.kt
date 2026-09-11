package io.github.nd2026.edited.lexer

/**
 * Extension point for consumers of a [MorphemeIndex] (POS-based syntax highlighting, the
 * linter, autocomplete, ...) - mirrors
 * [io.github.nd2026.edited.core.TextAreaListener]'s "override only what you need" convention.
 */
interface MorphemeIndexListener {
    fun onLineAnalyzed(line: Int, morphemes: List<Morpheme>) {}
}
