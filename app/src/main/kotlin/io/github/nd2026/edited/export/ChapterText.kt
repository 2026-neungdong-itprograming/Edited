package io.github.nd2026.edited.export

import io.github.nd2026.edited.core.Directives
import java.nio.file.Files
import java.nio.file.Path

/**
 * Turns a Markdown chapter into the plain text serial platforms expect when the author pastes it:
 * no `#`/`**`/`>` markup, trailing spaces removed, at most one blank line between paragraphs.
 */
object ChapterText {
    private val heading = Regex("^#{1,6}\\s+")
    private val quote = Regex("^>\\s?")
    private val bold = Regex("\\*\\*(.+?)\\*\\*|__(.+?)__")
    private val italic = Regex("(?<![*\\w])\\*(?!\\s)(.+?)(?<!\\s)\\*(?![*\\w])")
    private val strike = Regex("~~(.+?)~~")
    private val code = Regex("`([^`]+)`")
    private val link = Regex("\\[([^]]+)]\\([^)]*\\)")

    fun of(path: Path): String = plain(Files.readString(path))

    /**
     * One line of hashtags for a serial platform's tag box, e.g. `#서연 #마법학교` ([ChapterTags.of]: names first,
     * then common nouns). Empty when there is nothing to tag, so the caller can skip the line.
     */
    fun hashtags(text: String, limit: Int = 5): String = ChapterTags.of(text, limit).joinToString(" ") { "#$it" }

    fun plain(markdown: String): String {
        // Directives (@Suppress …) are notes to Texted; readers never see them.
        val lines = Directives.strip(markdown).lines().map { raw ->
            var line = raw.removeSuffix("\r").trimEnd()
            line = heading.replace(line, "")
            line = quote.replace(line, "")
            line = bold.replace(line) { it.groupValues[1].ifEmpty { it.groupValues[2] } }
            line = strike.replace(line, "$1")
            line = italic.replace(line, "$1")
            line = code.replace(line, "$1")
            link.replace(line, "$1")
        }
        val out = ArrayList<String>()
        for (line in lines) {
            if (line.isBlank() && (out.isEmpty() || out.last().isBlank())) continue
            out += line
        }
        while (out.isNotEmpty() && out.last().isBlank()) out.removeAt(out.lastIndex)
        return out.joinToString("\n")
    }
}
