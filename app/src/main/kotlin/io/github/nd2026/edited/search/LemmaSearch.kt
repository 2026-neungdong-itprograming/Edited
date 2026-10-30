package io.github.nd2026.edited.search

import io.github.nd2026.edited.core.Directives
import io.github.nd2026.edited.core.Parallel
import io.github.nd2026.edited.lexer.Morphology
import io.github.nd2026.edited.lexer.isEnding
import io.github.nd2026.edited.lexer.isParticle
import io.github.nd2026.edited.lexer.lemma
import io.github.nd2026.edited.project.Project
import java.nio.file.Files
import java.nio.file.Path

/**
 * Finds every inflected form of a word: "먹다" matches 먹었다, 먹고, 먹는. Nouns and other words match as
 * they are. Hits sit at the start of the matching morpheme. Needs Kiwi; without it nothing is found.
 */
object LemmaSearch {
    /**
     * Every use of [lemma] in the manuscript, in reading order. A conjugated query works too: "열었다" is
     * looked up as 열다. Chapters are analyzed in parallel; call it off the EDT.
     */
    fun find(project: Project, lemma: String): List<SearchHit> {
        val target = normalize(lemma)
        if (target.isEmpty() || !Morphology.isAvailable) return emptyList()
        Morphology.awaitDictionary()
        return Parallel.map(project.chapters()) { chapter ->
            runCatching { findIn(chapter.path, Files.readString(chapter.path), target) }.getOrDefault(emptyList())
        }.flatten()
    }

    /** The matches of [lemma] (any form of it) in one chapter's [text]; lines and columns are 0-based like [ProjectSearch]. */
    fun findIn(path: Path, text: String, lemma: String): List<SearchHit> {
        val target = normalize(lemma)
        if (target.isEmpty() || text.isEmpty() || !Morphology.isAvailable) return emptyList()
        val lines = text.split('\n').map { it.removeSuffix("\r") }
        val hits = ArrayList<SearchHit>()
        Morphology.analyzeLines(lines).forEachIndexed { line, morphemes ->
            val content = lines[line]
            val notes = Directives.inLine(content)
            val seen = HashSet<Int>()
            for (m in morphemes) {
                if (m.lemma != target || notes.any { m.start >= it.start && m.start < it.end } || !seen.add(m.start)) continue
                hits += SearchHit(path, line, m.start, ProjectSearch.preview(content, m.start))
            }
        }
        return hits
    }

    /**
     * The dictionary form to search for. A single word that Kiwi reads as one stem plus endings/particles
     * ("열었다" = 열 + 었 + 다) becomes that stem's lemma; anything else (a compound, a phrase) is used as typed.
     */
    internal fun normalize(query: String): String {
        val trimmed = query.trim()
        if (trimmed.isEmpty() || trimmed.any(Char::isWhitespace) || !Morphology.isAvailable) return trimmed
        val morphemes = Morphology.analyze(trimmed)
        val stems = morphemes.filter { !it.isEnding && !it.isParticle && it.partOfSpeech.first() in "NVMX" }
        return stems.singleOrNull()?.lemma ?: trimmed
    }
}
