package io.github.nd2026.edited.world

import io.github.nd2026.edited.core.Directives
import io.github.nd2026.edited.core.Parallel
import io.github.nd2026.edited.lexer.Morphology
import io.github.nd2026.edited.lexer.isProperNoun
import io.github.nd2026.edited.project.Project
import java.nio.file.Files

/** A proper noun that keeps appearing in the manuscript but has no character card yet. */
data class CharacterSuggestion(val name: String, val count: Int, val chapters: Int)

object CharacterSuggestions {
    /**
     * Proper nouns ([isProperNoun]) that appear at least [minCount] times in the manuscript, most frequent first (ties
     * alphabetically), minus every name and alias already in `characters/` and `world/`. A candidate that is part of a
     * known name (`서연` when the card is `한서연`) or contains one (`김민준` when `민준` has a card) is the same
     * person or thing, so it is left out too. One-letter names are Kiwi guessing and are skipped. Empty without Kiwi or
     * without a manuscript. Chapters are analyzed in parallel; call it off the EDT.
     */
    fun suggest(project: Project, minCount: Int = 3): List<CharacterSuggestion> {
        if (!Morphology.isAvailable) return emptyList()
        Morphology.awaitDictionary()
        val known = knownNames(project)
        val perChapter = Parallel.map(project.chapters()) { chapter ->
            properNouns(runCatching { Files.readString(chapter.path) }.getOrDefault(""))
        }
        val totals = HashMap<String, Int>()
        val chapterCounts = HashMap<String, Int>()
        for (counts in perChapter) for ((name, n) in counts) {
            totals.merge(name, n, Int::plus)
            chapterCounts.merge(name, 1, Int::plus)
        }
        return totals
            .filter { (name, n) -> n >= minCount && name.length >= 2 && known.none { sameAs(it, name) } }
            .map { (name, n) -> CharacterSuggestion(name, n, chapterCounts.getValue(name)) }
            .sortedWith(compareByDescending<CharacterSuggestion> { it.count }.thenBy { it.name })
    }

    private fun knownNames(project: Project): Set<String> {
        val characters = runCatching { CharacterRepository.load(project) }.getOrDefault(emptyList())
        val world = runCatching { WorldRepository.load(project) }.getOrDefault(emptyList())
        return (characters.flatMap { listOf(it.name) + it.aliases } + world.flatMap { listOf(it.name) + it.aliases })
            .map(String::trim).filter(String::isNotEmpty).toSet()
    }

    private fun sameAs(known: String, candidate: String): Boolean =
        known == candidate || (known.length >= 2 && (known.contains(candidate) || candidate.contains(known)))

    private fun properNouns(text: String): Map<String, Int> {
        val lines = Directives.strip(text).split('\n').map { it.removeSuffix("\r") }
        val counts = HashMap<String, Int>()
        for (m in Morphology.analyzeLines(lines).flatten()) if (m.isProperNoun) counts.merge(m.surface, 1, Int::plus)
        return counts
    }
}
