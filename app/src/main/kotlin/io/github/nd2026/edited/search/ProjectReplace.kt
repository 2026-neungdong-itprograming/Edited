package io.github.nd2026.edited.search

import io.github.nd2026.edited.lexer.Morpheme
import io.github.nd2026.edited.lexer.Morphology
import io.github.nd2026.edited.lexer.isParticle
import io.github.nd2026.edited.project.Project
import java.nio.file.Files
import java.nio.file.Path

/** A line that a replace-all would change; [line] is 0-based. */
data class ReplacePreview(val path: Path, val line: Int, val before: String, val after: String)

/** Replace-all across the manuscript, with particle agreement ([Josa]) when renaming. */
object ProjectReplace {
    /**
     * [text] with every [from] replaced by [to]. With [fixParticles] the particle after it is made to agree
     * ([Josa]): "민준이가" → "지아가", "민준을" → "지아를".
     *
     * 4-7. The particle is only touched when the morpheme right after the name is one: a particle (J*), the
     * name suffix `이` (XSN, "민준이가") or the copula `이` (VCP, "민준이었다"). So "민준아버지" or "민준호" -
     * where what follows is part of a word - stay as they are, and a name that is not on a morpheme boundary
     * ("하늘" inside "하늘색") gets no particle fix. Without Kiwi the check is skipped and [Josa] decides alone.
     */
    fun replaceInText(text: String, from: String, to: String, fixParticles: Boolean = true): String {
        if (from.isEmpty() || from !in text) return text
        return text.split('\n').joinToString("\n") { replaceInLine(it, from, to, fixParticles) }
    }

    private fun replaceInLine(line: String, from: String, to: String, fixParticles: Boolean): String {
        if (from !in line) return line
        // Kiwi is only asked when a particle might actually change, and at most once per line.
        val morphemes by lazy { if (Morphology.isAvailable) Morphology.analyze(line.removeSuffix("\r")) else emptyList() }
        val out = StringBuilder(line.length)
        var i = 0
        while (i < line.length) {
            if (!line.startsWith(from, i)) {
                out.append(line[i++])
                continue
            }
            out.append(to)
            val nameStart = i
            i += from.length
            if (fixParticles) {
                val (drop, insert) = Josa.adjust(from, to, line.substring(i, minOf(line.length, i + 4)))
                if ((drop != 0 || insert.isNotEmpty()) && particleFollows(morphemes, nameStart, i, drop)) {
                    out.append(insert)
                    i += drop
                }
            }
        }
        return out.toString()
    }

    /**
     * True if what follows the name (it spans [start] until [end]) is a particle that may be changed; also true
     * when there is no analysis to ask. [josaLength] is how many characters [Josa] would replace.
     *
     * Kiwi sometimes glues a particle onto an unknown name when it is the last word of the text ("지아로" as one
     * noun, where "지아로 갔다" is 지아 + 로): then the rest of that morpheme must be exactly the characters
     * [Josa] replaces, nothing more.
     */
    private fun particleFollows(morphemes: List<Morpheme>, start: Int, end: Int, josaLength: Int): Boolean {
        if (morphemes.isEmpty()) return true
        val next = morphemes.firstOrNull { it.start == end }
        if (next != null) return next.isParticle || next.partOfSpeech == "VCP" || (next.partOfSpeech == "XSN" && next.surface == "이")
        val glued = morphemes.firstOrNull { it.start == start && it.end > end } ?: return false
        return (glued.partOfSpeech == "NNG" || glued.partOfSpeech == "NNP") && glued.end - end == josaLength
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
