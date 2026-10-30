package io.github.nd2026.edited.inspect

import io.github.nd2026.edited.core.Inspection
import io.github.nd2026.edited.lexer.Morphology

/** Runs every [InspectionRule] over a document. Add new rules to [rules]. */
object Linter {
    val rules: List<InspectionRule> = listOf(
        LongSentenceRule(),
        QuotePairRule(),
        HabitWordRule(),
    )

    /**
     * Checks that read morphemes (see [MorphemeRule]). Without Kiwi every line comes back without morphemes:
     * the word and passive checks then report nothing, and [EndingRepetitionRule] counts syllables instead.
     */
    val morphemeRules: List<MorphemeRule> = listOf(
        EndingRepetitionRule(),
        RepeatedWordRule(),
        PassiveVoiceRule(),
    )

    fun run(text: String): List<Inspection> {
        val lines = text.split('\n').map { it.removeSuffix("\r") }
        val plain = rules.flatMap { it.check(lines) }
        val morphemes = Morphology.analyzeLines(lines)
        val withMorphemes = morphemeRules.flatMap { it.check(lines, morphemes) }
        return (plain + withMorphemes).sortedBy { it.line }
    }
}
