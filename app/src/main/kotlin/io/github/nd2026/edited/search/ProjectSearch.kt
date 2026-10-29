package io.github.nd2026.edited.search

import io.github.nd2026.edited.project.Project
import java.nio.file.Files
import java.nio.file.Path

/** One match; [line] and [column] are 0-based. [preview] is the trimmed line, shortened around the match. */
data class SearchHit(val path: Path, val line: Int, val column: Int, val preview: String)

object ProjectSearch {
    private const val PREVIEW_CONTEXT = 30

    /** Every non-overlapping occurrence of [query] in the manuscript, in reading order. */
    fun find(project: Project, query: String): List<SearchHit> {
        if (query.isEmpty()) return emptyList()
        return project.chapters().flatMap { chapter -> findIn(chapter.path, Files.readString(chapter.path), query) }
    }

    fun findIn(path: Path, text: String, query: String): List<SearchHit> {
        if (query.isEmpty()) return emptyList()
        val hits = ArrayList<SearchHit>()
        text.lines().forEachIndexed { line, raw ->
            val content = raw.removeSuffix("\r")
            var from = 0
            while (true) {
                val column = content.indexOf(query, from)
                if (column < 0) break
                hits += SearchHit(path, line, column, preview(content, column))
                from = column + query.length
            }
        }
        return hits
    }

    private fun preview(line: String, column: Int): String {
        val start = (column - PREVIEW_CONTEXT).coerceAtLeast(0)
        val end = (column + PREVIEW_CONTEXT * 2).coerceAtMost(line.length)
        return (if (start > 0) "…" else "") + line.substring(start, end).trim() + (if (end < line.length) "…" else "")
    }
}
