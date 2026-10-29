package io.github.nd2026.edited.inspect

import io.github.nd2026.edited.core.Inspection
import io.github.nd2026.edited.lexer.Morphology

/** Runs every [InspectionRule] over a document. Add new rules to [rules]. */
object Linter {
    val rules: List<InspectionRule> = listOf(
        LongSentenceRule(),
        EndingRepetitionRule(),
        QuotePairRule(),
        HabitWordRule(),
    )

    /** Checks that need morphemes (see [MorphemeRule]); skipped when Kiwi is unavailable. */
    val morphemeRules: List<MorphemeRule> = listOf(
        RepeatedWordRule(),
        PassiveVoiceRule(),
    )

    fun run(text: String): List<Inspection> {
        val lines = text.split('\n').map { it.removeSuffix("\r") }
        val plain = rules.flatMap { it.check(lines) }
        val withMorphemes = if (!Morphology.isAvailable) emptyList() else {
            val morphemes = Morphology.analyzeLines(lines)
            morphemeRules.flatMap { it.check(lines, morphemes) }
        }
        return (plain + withMorphemes).sortedBy { it.line }
    }
}
