package io.github.nd2026.edited.inspect

import io.github.nd2026.edited.core.Inspection
import io.github.nd2026.edited.lexer.Morpheme

/**
 * A linter check that needs morphemes. [morphemes] has one list per entry of [lines], with offsets
 * relative to that line (see `lexer/Morphology.kt` for the tag helpers). Reported lines are 0-based.
 */
interface MorphemeRule {
    val id: String
    fun check(lines: List<String>, morphemes: List<List<Morpheme>>): List<Inspection>
}
