package io.github.nd2026.edited.search

import io.github.nd2026.edited.project.Project
import java.nio.file.Files
import java.nio.file.Path

/** A line that a replace-all would change; [line] is 0-based. */
data class ReplacePreview(val path: Path, val line: Int, val before: String, val after: String)

/** Replace-all across the manuscript, with particle agreement ([Josa]) when renaming. */
object ProjectReplace {
    fun replaceInText(text: String, from: String, to: String, fixParticles: Boolean = true): String {
        if (from.isEmpty()) return text
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            if (text.startsWith(from, i)) {
                out.append(to)
                i += from.length
                if (fixParticles) {
                    val (drop, insert) = Josa.adjust(from, to, text.substring(i, minOf(text.length, i + 4)))
                    out.append(insert)
                    i += drop
                }
            } else {
                out.append(text[i++])
            }
        }
        return out.toString()
    }

    fun preview(project: Project, from: String, to: String, fixParticles: Boolean = true): List<ReplacePreview> {
        if (from.isEmpty()) return emptyList()
        return project.chapters().flatMap { chapter ->
            Files.readString(chapter.path).lines().mapIndexedNotNull { line, before ->
                if (from !in before) return@mapIndexedNotNull null
                ReplacePreview(chapter.path, line, before.trim(), replaceInText(before, from, to, fixParticles).trim())
            }
        }
    }

    /** Rewrites every chapter containing [from]; returns the number of files changed. */
    fun apply(project: Project, from: String, to: String, fixParticles: Boolean = true): Int {
        if (from.isEmpty()) return 0
        var changed = 0
        for (chapter in project.chapters()) {
            val before = Files.readString(chapter.path)
            val after = replaceInText(before, from, to, fixParticles)
            if (after != before) { Files.writeString(chapter.path, after); changed++ }
        }
        return changed
    }
}
