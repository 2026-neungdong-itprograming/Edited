package io.github.nd2026.edited.spell

import io.github.nd2026.edited.core.Directives
import io.github.nd2026.edited.core.Inspection
import io.github.nd2026.edited.core.Severity
import io.github.nd2026.edited.core.SuppressionScope
import io.github.nd2026.edited.inspect.Linter
import io.github.nd2026.edited.lexer.Morphology

/**
 * Every check of a chapter, as the Problems pane shows them: [Linter]'s rules plus [SpellChecker],
 * minus what `@Suppress` directives silence (see [Directives]).
 *
 * The rules see the text with directives removed (line numbers unchanged), so `@Suppress("따옴표")`'s
 * own quotes or its length never trip a rule. Linter rules report whole lines, so a sentence-level
 * `@Suppress` silences that rule for the whole line it is on.
 */
object Proofreader {
    fun run(text: String): List<Inspection> {
        val lines = text.split('\n').map { it.removeSuffix("\r") }
        val scope = SuppressionScope.of(lines)
        val plain = Directives.stripLines(lines)

        val found = ArrayList<Inspection>()
        for (rule in Linter.rules) {
            rule.check(plain).filterNotTo(found) { scope.isSuppressed(rule.id, it.line) }
        }
        val morphemes = Morphology.analyzeLines(plain)
        for (rule in Linter.morphemeRules) {
            rule.check(plain, morphemes).filterNotTo(found) { scope.isSuppressed(rule.id, it.line) }
        }
        lines.forEachIndexed { line, lineText ->
            for (m in SpellChecker.check(lineText)) {
                if (!scope.isSuppressed(SpellChecker.CHECK_ID, line, m.start)) found += Inspection(line, Severity.ERROR, m.message)
            }
        }
        return found.sortedBy { it.line }
    }
}
