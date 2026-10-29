package io.github.nd2026.edited.inspect

import io.github.nd2026.edited.core.Inspection
import io.github.nd2026.edited.core.Sentences
import io.github.nd2026.edited.core.Severity

/** Warns about sentences longer than [maxChars] characters - they are hard to read on a phone screen. */
class LongSentenceRule(private val maxChars: Int = 120) : InspectionRule {
    override val id = "long-sentence"

    override fun check(lines: List<String>): List<Inspection> = lines.flatMapIndexed { line, text ->
        Sentences.split(text)
            .filter { it.length > maxChars }
            .map { Inspection(line, Severity.WARNING, "문장이 너무 깁니다 (${it.length}자, 권장 ${maxChars}자 이하)") }
    }
}
