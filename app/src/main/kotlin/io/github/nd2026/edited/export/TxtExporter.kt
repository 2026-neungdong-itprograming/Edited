package io.github.nd2026.edited.export

import io.github.nd2026.edited.project.Project
import java.nio.file.Files
import java.nio.file.Path

/** Joins all chapters, in reading order, into one UTF-8 text file with two blank lines between chapters. */
object TxtExporter {
    fun export(project: Project, target: Path, stripMarkdown: Boolean = true) {
        target.toAbsolutePath().parent?.let(Files::createDirectories)
        Files.writeString(target, render(project, stripMarkdown))
    }

    fun render(project: Project, stripMarkdown: Boolean = true): String =
        project.chapters().joinToString("\n\n\n") { chapter ->
            val text = Files.readString(chapter.path)
            if (stripMarkdown) ChapterText.plain(text) else text.trim()
        } + "\n"
}
