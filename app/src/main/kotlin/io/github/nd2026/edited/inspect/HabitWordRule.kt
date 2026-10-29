package io.github.nd2026.edited.inspect

import io.github.nd2026.edited.core.Inspection
import io.github.nd2026.edited.core.Severity

/** Points out habit words used [threshold] or more times within one paragraph (line). */
class HabitWordRule(
    private val words: List<String> = DEFAULT_WORDS,
    private val threshold: Int = 3,
) : InspectionRule {
    override val id = "habit-word"

    override fun check(lines: List<String>): List<Inspection> = lines.flatMapIndexed { line, text ->
        words.mapNotNull { word ->
            val count = text.windowed(word.length).count { it == word }
            if (count >= threshold) Inspection(line, Severity.INFO, "'$word'이(가) 한 문단에 ${count}번 나옵니다") else null
        }
    }

    companion object {
        val DEFAULT_WORDS = listOf("갑자기", "순간", "그리고", "문득", "천천히", "조금")
    }
}
