package io.github.nd2026.edited.inspect

import io.github.nd2026.edited.core.Inspection
import io.github.nd2026.edited.lexer.Morpheme

/** 1-6. The same content word (by lemma) used again within a few sentences of one paragraph → INFO. */
class RepeatedWordRule : MorphemeRule {
    override val id = "repeated-word"

    // TODO(작업 1-6)
    override fun check(lines: List<String>, morphemes: List<List<Morpheme>>): List<Inspection> = emptyList()
}
