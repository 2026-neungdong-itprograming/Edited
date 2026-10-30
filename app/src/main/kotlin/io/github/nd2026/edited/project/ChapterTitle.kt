package io.github.nd2026.edited.project

import java.nio.file.Path

/**
 * The display title of a chapter. Texted's own formats ([NovelFormat]) have no markup, so theirs is the
 * file name without extension and order number (`03. 낯선 손님.episode` → `낯선 손님`); Markdown and
 * text files use their first Markdown heading, or the file name without extension.
 */
object ChapterTitle {
    private val heading = Regex("^#{1,6}\\s+(.+)$")
    private val orderPrefix = Regex("^\\d+[.)]\\s*")

    fun of(path: Path, text: String): String {
        val baseName = path.fileName.toString().substringBeforeLast('.')
        if (NovelFormat.of(path) != null) return baseName.replaceFirst(orderPrefix, "").ifBlank { baseName }
        return text.lineSequence().firstNotNullOfOrNull { heading.find(it.trim())?.groupValues?.get(1)?.trim() } ?: baseName
    }

    fun isHeading(line: String): Boolean = heading.matches(line.trim())
}
