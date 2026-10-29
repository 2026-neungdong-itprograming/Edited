package io.github.nd2026.edited.project

import java.nio.file.Path

/** The display title of a chapter: its first Markdown heading, or the file name without extension. */
object ChapterTitle {
    private val heading = Regex("^#{1,6}\\s+(.+)$")

    fun of(path: Path, text: String): String =
        text.lineSequence().firstNotNullOfOrNull { heading.find(it.trim())?.groupValues?.get(1)?.trim() }
            ?: path.fileName.toString().substringBeforeLast('.')

    fun isHeading(line: String): Boolean = heading.matches(line.trim())
}
