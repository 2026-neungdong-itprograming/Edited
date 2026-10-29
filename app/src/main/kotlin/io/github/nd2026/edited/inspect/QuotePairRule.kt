package io.github.nd2026.edited.inspect

import io.github.nd2026.edited.core.Inspection
import io.github.nd2026.edited.core.Severity

/** Reports lines (paragraphs) whose quotes or brackets are not closed - usually a typo in dialogue. */
class QuotePairRule : InspectionRule {
    override val id = "quote-pair"

    override fun check(lines: List<String>): List<Inspection> = lines.mapIndexedNotNull { line, text ->
        val broken = buildList {
            if (text.count { it == '"' } % 2 != 0) add("\" \"")
            if (text.count { it == '“' } != text.count { it == '”' }) add("“ ”")
            if (text.count { it == '‘' } != text.count { it == '’' }) add("‘ ’")
            if (text.count { it == '(' } != text.count { it == ')' }) add("( )")
            if (text.count { it == '「' } != text.count { it == '」' }) add("「 」")
        }
        if (broken.isEmpty()) null
        else Inspection(line, Severity.ERROR, "짝이 맞지 않습니다: ${broken.joinToString(", ")}")
    }
}
