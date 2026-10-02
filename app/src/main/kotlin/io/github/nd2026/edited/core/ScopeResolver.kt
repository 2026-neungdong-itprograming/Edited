package io.github.nd2026.edited.core

/** One level of the "where am I in the manuscript" path shown by the breadcrumb. */
data class Scope(val level: Int, val title: String, val line: Int)

/**
 * Finds the part/chapter/scene containing a line by scanning backwards for headings, so the cost is
 * bounded by [maxScanLines] (not by manuscript size) and nothing has to be indexed or kept in sync.
 *
 * Recognised headings: Markdown `#`..`######` (level = number of `#`), plain `제N부` (level 1) and
 * `제N장` / `제N화` (level 2), and scene breaks such as `***`, `---`, `◇◇◇` (level [SCENE]).
 */
object ScopeResolver {
    const val SCENE = 9

    private val partHeading = Regex("^제\\s*\\d+\\s*부.*")
    private val chapterHeading = Regex("^제\\s*\\d+\\s*[장화].*")
    private val sceneBreak = Regex("^\\s*([*\\-_◇◆※]\\s*){3,}$")

    fun resolve(textArea: TextArea, line: Int, maxScanLines: Int = 20_000): List<Scope> {
        if (textArea.lineCount == 0) return emptyList()
        val start = line.coerceIn(0, textArea.lineCount - 1)
        val chain = ArrayList<Scope>()
        var minLevel = Int.MAX_VALUE
        var sceneBreaks = 0
        var nearestBreak = -1
        for (l in start downTo maxOf(0, start - maxScanLines)) {
            val heading = headingAt(textArea, l) ?: continue
            if (heading.level == SCENE) {
                if (chain.isEmpty()) {
                    sceneBreaks++
                    if (nearestBreak < 0) nearestBreak = l
                }
                continue
            }
            if (heading.level < minLevel) {
                chain += heading
                minLevel = heading.level
                if (minLevel == 1) break
            }
        }
        chain.reverse()
        if (sceneBreaks > 0) chain += Scope(SCENE, "장면 ${sceneBreaks + 1}", nearestBreak)
        return chain
    }

    /** The heading on [line], or null. Looks at the first character before allocating the line. */
    fun headingAt(textArea: TextArea, line: Int): Scope? {
        val start = textArea.lineStart(line)
        val end = textArea.lineEnd(line)
        if (end <= start) return null
        val first = textArea.charAt(start)
        if (first != '#' && first != '제' && first !in "*-_◇◆※ \t" || end - start > 200) return null
        val text = textArea.lineText(line)
        return when {
            first == '#' -> markdownHeading(text, line)
            partHeading.matches(text) -> Scope(1, text.trim(), line)
            chapterHeading.matches(text) -> Scope(2, text.trim(), line)
            text.length <= 16 && sceneBreak.matches(text) -> Scope(SCENE, text.trim(), line)
            else -> null
        }
    }

    private fun markdownHeading(text: String, line: Int): Scope? {
        val level = text.takeWhile { it == '#' }.length
        if (level > 6 || text.getOrNull(level) != ' ') return null
        val title = text.substring(level).trim()
        return if (title.isEmpty()) null else Scope(level, title, line)
    }
}
