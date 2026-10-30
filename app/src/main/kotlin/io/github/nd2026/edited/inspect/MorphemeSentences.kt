package io.github.nd2026.edited.inspect

import io.github.nd2026.edited.lexer.Morpheme

/** Splits one line's morphemes into sentences: after a sentence-final mark (SF `.?!`, SE `…`) and the quotes/brackets that close it. */
internal object MorphemeSentences {
    private const val CLOSERS = "\"'”’)]」』》〉"

    fun split(morphemes: List<Morpheme>): List<List<Morpheme>> {
        val result = ArrayList<List<Morpheme>>()
        var current = ArrayList<Morpheme>()
        var ended = false
        for (m in morphemes) {
            if (ended && !(m.surface.length == 1 && m.surface[0] in CLOSERS)) {
                result += current
                current = ArrayList()
                ended = false
            }
            current += m
            if (m.partOfSpeech == "SF" || m.partOfSpeech == "SE") ended = true
        }
        if (current.isNotEmpty()) result += current
        return result
    }
}
