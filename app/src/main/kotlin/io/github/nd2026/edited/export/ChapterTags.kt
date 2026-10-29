package io.github.nd2026.edited.export

import io.github.nd2026.edited.project.Project

/** Keyword tags for a chapter or the whole book (e.g. EPUB subjects, serial-site hashtags). */
object ChapterTags {
    // TODO(작업 5-5): the most frequent proper nouns, then common nouns, skipping one-letter and stop words.
    fun of(text: String, limit: Int = 5): List<String> = emptyList()

    // TODO(작업 5-6)
    fun ofProject(project: Project, limit: Int = 8): List<String> = emptyList()
}
