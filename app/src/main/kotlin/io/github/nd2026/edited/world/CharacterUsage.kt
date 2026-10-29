package io.github.nd2026.edited.world

import io.github.nd2026.edited.project.ChapterTitle
import io.github.nd2026.edited.project.Project
import java.nio.file.Files
import java.nio.file.Path

/** How often a character appears in one chapter. */
data class Appearance(val chapter: Path, val title: String, val count: Int)

object CharacterUsage {
    /** Chapters (in reading order) that mention [sheet] by name or alias; chapters without a mention are left out. */
    fun appearances(project: Project, sheet: CharacterSheet): List<Appearance> = project.chapters().mapNotNull { chapter ->
        val text = Files.readString(chapter.path)
        val count = count(text, listOf(sheet.name) + sheet.aliases)
        if (count > 0) Appearance(chapter.path, ChapterTitle.of(chapter.path, text), count) else null
    }

    /**
     * Counts non-overlapping mentions of any of [terms]. Longer terms win, so with the name `김민준`
     * and the alias `민준`, "김민준" counts once rather than twice.
     */
    fun count(text: String, terms: List<String>): Int {
        val sorted = terms.filter(String::isNotBlank).distinct().sortedByDescending { it.length }
        if (sorted.isEmpty()) return 0
        var count = 0
        var i = 0
        while (i < text.length) {
            val term = sorted.firstOrNull { text.startsWith(it, i) }
            if (term != null) { count++; i += term.length } else i++
        }
        return count
    }
}
