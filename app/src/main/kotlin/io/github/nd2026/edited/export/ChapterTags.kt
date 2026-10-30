package io.github.nd2026.edited.export

import io.github.nd2026.edited.core.Parallel
import io.github.nd2026.edited.lexer.CommonWords
import io.github.nd2026.edited.lexer.Morphology
import io.github.nd2026.edited.project.Project
import java.nio.file.Files

/** Keyword tags for a chapter or the whole book (e.g. EPUB subjects, serial-site hashtags). */
object ChapterTags {
    /** Proper nouns and common nouns of one text with their counts; see [of]. */
    private class Counts(val proper: Map<String, Int>, val common: Map<String, Int>)

    /**
     * Up to [limit] tags for [text]: the most used proper nouns (names of people and places) first, then the most
     * used common nouns. One-letter words and the everyday nouns in [CommonWords.COMMON_NOUNS] (것, 수, 때, 말 …)
     * never make it. Ties keep the order of first appearance. Empty without Kiwi.
     */
    fun of(text: String, limit: Int = 5): List<String> = rank(listOf(countsOf(text)), limit)

    /**
     * Up to [limit] tags for the whole manuscript, chosen the same way as [of] from the counts of every chapter
     * (chapters are analyzed in parallel). Used as the EPUB's subjects.
     */
    fun ofProject(project: Project, limit: Int = 8): List<String> =
        rank(Parallel.map(project.chapters()) { countsOf(runCatching { Files.readString(it.path) }.getOrDefault("")) }, limit)

    private fun countsOf(text: String): Counts {
        val proper = LinkedHashMap<String, Int>()
        val common = LinkedHashMap<String, Int>()
        if (text.isNotBlank() && Morphology.isAvailable) {
            // Plain text: no markup, no directives, so `@Suppress` and `#` headings are never tags.
            val lines = ChapterText.plain(text).split('\n')
            for (m in Morphology.analyzeLines(lines).flatten()) {
                if (m.surface.length < 2) continue
                when (m.partOfSpeech) {
                    "NNP" -> proper.merge(m.surface, 1, Int::plus)
                    "NNG" -> if (m.surface !in CommonWords.COMMON_NOUNS) common.merge(m.surface, 1, Int::plus)
                }
            }
        }
        return Counts(proper, common)
    }

    private fun rank(all: List<Counts>, limit: Int): List<String> {
        if (limit <= 0) return emptyList()
        fun top(pick: (Counts) -> Map<String, Int>): List<String> {
            val total = LinkedHashMap<String, Int>()
            for (counts in all) for ((word, n) in pick(counts)) total.merge(word, n, Int::plus)
            // sortedByDescending is stable, so equal counts stay in first-appearance order.
            return total.entries.sortedByDescending { it.value }.map { it.key }
        }
        val proper = top { it.proper }
        return (proper + top { it.common }.filter { it !in proper }).take(limit)
    }
}
