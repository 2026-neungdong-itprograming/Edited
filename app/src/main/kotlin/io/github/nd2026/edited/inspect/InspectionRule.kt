package io.github.nd2026.edited.inspect

import io.github.nd2026.edited.core.Inspection

/** One check of the manuscript linter. [check] gets the document's lines; reported lines are 0-based. */
interface InspectionRule {
    val id: String
    fun check(lines: List<String>): List<Inspection>
}
