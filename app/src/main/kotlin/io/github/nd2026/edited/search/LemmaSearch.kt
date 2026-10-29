package io.github.nd2026.edited.search

import io.github.nd2026.edited.project.Project

/** Finds every inflected form of a word: "먹다" matches 먹었다, 먹고, 먹는. */
object LemmaSearch {
    // TODO(작업 4-5): analyze each chapter line, match Morpheme.lemma == [lemma] (or the surface for nouns),
    //  and report the match's line/column like ProjectSearch.
    fun find(project: Project, lemma: String): List<SearchHit> = emptyList()
}
