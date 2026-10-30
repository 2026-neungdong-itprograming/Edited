package io.github.nd2026.edited.inspect

import io.github.nd2026.edited.core.Inspection
import io.github.nd2026.edited.core.Sentences
import io.github.nd2026.edited.core.Severity
import io.github.nd2026.edited.lexer.Morpheme

/**
 * Flags [minRun] or more consecutive sentences with the same ending, which makes prose monotonous. Reported
 * once per run, on the line where the run reaches [minRun].
 *
 * 1-7. With morphemes the ending is the sentence's last EF plus the EP right before it: `었`+`다` for 했다,
 * 갔다 and 먹었다 alike. [check] without morphemes (Kiwi unavailable, or a line it could not analyze) falls
 * back to the last two syllables (`~했다.` ×3), as before.
 */
class EndingRepetitionRule(private val minRun: Int = 3) : InspectionRule, MorphemeRule {
    override val id = "ending-repetition"

    override fun check(lines: List<String>): List<Inspection> = check(lines, emptyList())

    override fun check(lines: List<String>, morphemes: List<List<Morpheme>>): List<Inspection> {
        val result = ArrayList<Inspection>()
        var previous: String? = null
        var run = 0
        // Blank lines don't reset the run: web novels usually put every sentence in its own paragraph.
        lines.forEachIndexed { line, text ->
            for (ending in endingsOf(text, morphemes.getOrElse(line) { emptyList() })) {
                run = if (ending != null && ending == previous) run + 1 else 1
                previous = ending
                if (ending != null && run == minRun) {
                    result.add(Inspection(line, Severity.INFO, "같은 어미 '$ending'(으)로 끝나는 문장이 ${minRun}번 이상 이어집니다"))
                }
            }
        }
        return result
    }

    /** The ending of each sentence of one line, null for a sentence without one. */
    private fun endingsOf(text: String, morphemes: List<Morpheme>): List<String?> =
        if (morphemes.isEmpty()) Sentences.split(text).map(::endingOf)
        else MorphemeSentences.split(morphemes).map(::morphemeEndingOf)

    /** The last two Hangul syllables of [sentence], ignoring punctuation and quotes; null if it has fewer. */
    internal fun endingOf(sentence: String): String? {
        val syllables = sentence.filter { it in '가'..'힣' }
        return if (syllables.length >= 2) syllables.takeLast(2) else null
    }

    /** The final ending (EF) with the tense/honorific endings (EP) before it: "었다", "겠습니다"; null without an EF. */
    internal fun morphemeEndingOf(sentence: List<Morpheme>): String? {
        val last = sentence.indexOfLast { it.partOfSpeech == "EF" }
        if (last < 0) return null
        var first = last
        while (first > 0 && sentence[first - 1].partOfSpeech == "EP") first--
        return sentence.subList(first, last + 1).joinToString("") { it.surface }
    }
}
