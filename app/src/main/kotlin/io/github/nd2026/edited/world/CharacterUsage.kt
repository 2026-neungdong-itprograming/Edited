package io.github.nd2026.edited.world

import io.github.nd2026.edited.core.Directives
import io.github.nd2026.edited.core.Parallel
import io.github.nd2026.edited.lexer.Morpheme
import io.github.nd2026.edited.lexer.Morphology
import io.github.nd2026.edited.project.ChapterTitle
import io.github.nd2026.edited.project.Project
import java.nio.file.Files
import java.nio.file.Path

/** How often a character appears in one chapter. */
data class Appearance(val chapter: Path, val title: String, val count: Int)

object CharacterUsage {
    /**
     * Chapters (in reading order) that mention [sheet] by name or alias; chapters without a mention are left out.
     * Chapters are read and analyzed in parallel; call it off the EDT.
     */
    fun appearances(project: Project, sheet: CharacterSheet): List<Appearance> {
        Morphology.awaitDictionary()
        val terms = listOf(sheet.name) + sheet.aliases
        return Parallel.map(project.chapters()) { chapter ->
            val text = Files.readString(chapter.path)
            val count = count(text, terms)
            if (count > 0) Appearance(chapter.path, ChapterTitle.of(chapter.path, text), count) else null
        }.filterNotNull()
    }

    /**
     * Counts non-overlapping mentions of any of [terms]. Longer terms win, so with the name `김민준`
     * and the alias `민준`, "김민준" counts once rather than twice.
     *
     * 3-7. A mention must be made of whole morphemes: it starts where a morpheme starts and ends where one
     * ends. So the alias `연아` is not found inside `서연아` (서연 + 아), and the name `하늘` is not found
     * inside `하늘색`. A line Kiwi gave no morphemes for (Kiwi unavailable) is counted by characters, as before.
     * Directives (`@Suppress …`) are not part of the text.
     */
    fun count(text: String, terms: List<String>): Int {
        val sorted = terms.filter(String::isNotBlank).distinct().sortedByDescending { it.length }
        if (sorted.isEmpty()) return 0
        val lines = Directives.strip(text).split('\n').map { it.removeSuffix("\r") }
        val morphemes = Morphology.analyzeLines(lines)
        return lines.indices.sumOf { countInLine(lines[it], morphemes[it], sorted) }
    }

    private fun countInLine(line: String, morphemes: List<Morpheme>, sortedTerms: List<String>): Int {
        if (line.isEmpty()) return 0
        val aligned = morphemes.isNotEmpty()
        val starts = if (aligned) morphemes.mapTo(HashSet()) { it.start } else emptySet()
        val ends = if (aligned) morphemes.mapTo(HashSet()) { it.end } else emptySet()
        var count = 0
        var i = 0
        while (i < line.length) {
            val term = sortedTerms.firstOrNull { line.startsWith(it, i) && (!aligned || (i in starts && i + it.length in ends)) }
            if (term != null) { count++; i += term.length } else i++
        }
        return count
    }
}
