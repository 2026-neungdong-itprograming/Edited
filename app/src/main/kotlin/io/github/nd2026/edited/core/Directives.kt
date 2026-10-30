package io.github.nd2026.edited.core

/**
 * One directive in a manuscript line: `@` followed by a name and optional arguments, e.g.
 * `@Suppress("맞춤법")`, `@무시`, `@file:Suppress("긴 문장", "습관어")`. [start]/[end] are columns
 * in the line (end exclusive). Directives are notes to Texted, not prose: the editor folds them,
 * and character counts, inspections and exports leave them out.
 */
data class Directive(val start: Int, val end: Int, val name: String, val args: List<String>, val fileLevel: Boolean) {
    val isSuppress: Boolean get() = name.equals("Suppress", ignoreCase = true) || name == "무시"
}

/**
 * Parses directives and decides what `@Suppress` covers. Where a directive is placed sets its scope,
 * the way Kotlin annotations work:
 *
 * ```
 * @file:Suppress("맞춤법")              at the top of the file (before any prose): the whole file
 * @Suppress("긴 문장")                 on a line of its own: the next line
 * 그는 웃었다. @Suppress 됬다고 했다.   in front of a sentence: that sentence only
 * ```
 *
 * Arguments name checks (see [checkMatches]); `@Suppress` without arguments silences every check.
 * An `@` right after a letter or digit (an e-mail address) is not a directive.
 */
object Directives {
    private val pattern = Regex("""(?<![\p{L}\p{N}_])@(file:)?([\p{L}_][\p{L}\p{N}_]*)(\(([^()\n]*)\))?""")
    private val argument = Regex(""""([^"]*)"|“([^”]*)”|'([^']*)'|([^,\s"“'][^,]*)""")
    private const val TERMINATORS = ".!?…。"
    private const val CLOSERS = "\"'”’)]」』》〉"

    /** Lines scanned from the top for `@file:` directives (the header ends at the first line of prose). */
    const val HEADER_LIMIT = 200

    /** Check names and aliases → check id. Ids are also accepted as written ("long-sentence"). */
    private val ALIASES: Map<String, String> = mapOf(
        "맞춤법" to "spelling", "철자" to "spelling",
        "긴문장" to "long-sentence",
        "어미반복" to "ending-repetition", "어미" to "ending-repetition",
        "따옴표" to "quote-pair", "괄호" to "quote-pair", "짝" to "quote-pair",
        "습관어" to "habit-word",
        "낱말반복" to "repeated-word", "단어반복" to "repeated-word",
        "이중피동" to "double-passive", "피동" to "double-passive",
    )
    private val ALL = setOf("all", "전부", "모두", "*")

    fun inLine(line: String): List<Directive> {
        if ('@' !in line) return emptyList()
        return pattern.findAll(line).map { m ->
            val args = m.groups[4]?.value?.let { raw ->
                argument.findAll(raw).mapNotNull { a -> a.groupValues.drop(1).firstOrNull { it.isNotEmpty() }?.trim()?.takeIf(String::isNotEmpty) }.toList()
            }.orEmpty()
            Directive(m.range.first, m.range.last + 1, m.groupValues[2], args, m.groups[1] != null)
        }.toList()
    }

    /** True when [line] holds directives and nothing else; such a line annotates the line below. */
    fun isDirectiveOnly(line: String): Boolean {
        val directives = inLine(line)
        if (directives.isEmpty()) return false
        var column = 0
        for (d in directives) {
            if (line.substring(column, d.start).isNotBlank()) return false
            column = d.end
        }
        return line.substring(column).isBlank()
    }

    /** [line] without its directives (and the space each one leaves behind). */
    fun stripLine(line: String): String {
        val directives = inLine(line)
        if (directives.isEmpty()) return line
        val out = StringBuilder(line.length)
        var column = 0
        for (d in directives) {
            out.append(line, column, d.start)
            column = d.end
            // "그는 @Suppress 됬다" → "그는 됬다": drop the space after a directive that follows a space or starts the line.
            if (out.isEmpty() || out.last().isWhitespace()) while (column < line.length && line[column] == ' ') column++
        }
        out.append(line, column, line.length)
        return out.toString()
    }

    /** The same lines without directives; directive-only lines become empty, so line numbers stay put. */
    fun stripLines(lines: List<String>): List<String> = lines.map { if ('@' in it) stripLine(it) else it }

    /** [text] without directives; lines that held only directives are removed entirely. */
    fun strip(text: String): String {
        if ('@' !in text) return text
        return text.split('\n').filterNot { isDirectiveOnly(it.removeSuffix("\r")) }.joinToString("\n") { stripLine(it) }
    }

    /** Whether a `@Suppress` with [args] silences the check [checkId]: no arguments or "전부" means every check. */
    fun checkMatches(args: List<String>, checkId: String): Boolean {
        if (args.isEmpty()) return true
        val id = normalize(checkId)
        return args.any { raw ->
            val arg = normalize(raw)
            arg in ALL || arg == id || ALIASES[arg]?.let(::normalize) == id
        }
    }

    private fun normalize(name: String) = name.lowercase().filterNot { it.isWhitespace() || it == '-' || it == '_' || it == '·' }

    /**
     * The columns of [line] that a sentence-level directive [d] covers: from the directive to the end of the
     * sentence after it (its terminator and closing quotes included), or to the end of the line.
     */
    fun sentenceScope(line: String, d: Directive): IntRange {
        var i = d.end
        while (i < line.length && line[i] !in TERMINATORS) i++
        while (i < line.length && (line[i] in TERMINATORS || line[i] in CLOSERS)) i++
        return d.start until i
    }
}

/**
 * What `@Suppress` silences in one document. Lines are read through [lineText] on demand - only the
 * file header, the lines right above a line and the line itself - so the editor can ask about any
 * visible line of a 10M-character manuscript without scanning it.
 */
class SuppressionScope(private val lineCount: Int, private val lineText: (Int) -> String) {
    /** Arguments of every `@file:Suppress` in the header. */
    private val fileArgs: List<List<String>> by lazy {
        val found = ArrayList<List<String>>()
        var line = 0
        while (line < lineCount && line < Directives.HEADER_LIMIT) {
            val text = lineText(line)
            if (text.isNotBlank() && !Directives.isDirectiveOnly(text)) break
            Directives.inLine(text).filter { it.fileLevel && it.isSuppress }.forEach { found += it.args }
            line++
        }
        found
    }

    /** Whether [checkId] is silenced on [line]; with a [column], sentence-level directives must cover it, else any on the line counts. */
    fun isSuppressed(checkId: String, line: Int, column: Int = -1): Boolean {
        if (fileArgs.any { Directives.checkMatches(it, checkId) }) return true
        var above = line - 1
        while (above >= 0) {
            val text = lineText(above)
            if (!Directives.isDirectiveOnly(text)) break
            if (Directives.inLine(text).any { it.isSuppress && !it.fileLevel && Directives.checkMatches(it.args, checkId) }) return true
            above--
        }
        val text = lineText(line)
        if (Directives.isDirectiveOnly(text)) return false
        return Directives.inLine(text).any { d ->
            d.isSuppress && !d.fileLevel && Directives.checkMatches(d.args, checkId) &&
                (column < 0 || column in Directives.sentenceScope(text, d))
        }
    }

    companion object {
        fun of(lines: List<String>) = SuppressionScope(lines.size) { lines[it] }
    }
}
