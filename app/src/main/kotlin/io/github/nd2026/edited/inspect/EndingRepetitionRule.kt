package io.github.nd2026.edited.inspect

import io.github.nd2026.edited.core.Inspection
import io.github.nd2026.edited.core.Sentences
import io.github.nd2026.edited.core.Severity

/**
 * Flags [minRun] or more consecutive sentences ending in the same last two syllables (`~했다.` x3),
 * which makes prose monotonous. Reported once per run, on the line where the run reaches [minRun].
 */
class EndingRepetitionRule(private val minRun: Int = 3) : InspectionRule {
    override val id = "ending-repetition"

    override fun check(lines: List<String>): List<Inspection> {
        val result = ArrayList<Inspection>()
        var previous: String? = null
        var run = 0
        // Blank lines don't reset the run: web novels usually put every sentence in its own paragraph.
        lines.forEachIndexed { line, text ->
            for (sentence in Sentences.split(text)) {
                val ending = endingOf(sentence)
                run = if (ending != null && ending == previous) run + 1 else 1
                previous = ending
                if (ending != null && run == minRun) {
                    result.add(Inspection(line, Severity.INFO, "같은 어미 '$ending'(으)로 끝나는 문장이 ${minRun}번 이상 이어집니다"))
                }
            }
        }
        return result
    }

    /** The last two Hangul syllables of [sentence], ignoring punctuation and quotes; null if it has fewer. */
    internal fun endingOf(sentence: String): String? {
        val syllables = sentence.filter { it in '가'..'힣' }
        return if (syllables.length >= 2) syllables.takeLast(2) else null
    }
}
