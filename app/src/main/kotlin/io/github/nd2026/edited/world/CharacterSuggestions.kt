package io.github.nd2026.edited.world

import io.github.nd2026.edited.project.Project

/** A proper noun that keeps appearing in the manuscript but has no character card yet. */
data class CharacterSuggestion(val name: String, val count: Int, val chapters: Int)

object CharacterSuggestions {
    // TODO(작업 3-5): proper nouns (Morpheme.isProperNoun) appearing at least [minCount] times,
    //  minus names and aliases already in characters/ and world/, most frequent first.
    fun suggest(project: Project, minCount: Int = 3): List<CharacterSuggestion> = emptyList()
}
