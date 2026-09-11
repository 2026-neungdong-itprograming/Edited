package io.github.nd2026.edited.lexer

/**
 * Turns Korean text into morphemes. Kept as a pure, stateless-per-call interface - like
 * [io.github.nd2026.edited.core.TextBuffer] - so the Nori-backed implementation can be swapped
 * for a fake in tests without either side knowing about Lucene.
 */
interface MorphemeAnalyzer {
    /**
     * Analyzes [text] in isolation. [baseOffset] is added to every returned offset so the
     * result can be spliced straight into a document-wide index without the analyzer needing to
     * know where in the document [text] came from.
     */
    fun analyze(text: String, baseOffset: Int = 0): List<Morpheme>
}
