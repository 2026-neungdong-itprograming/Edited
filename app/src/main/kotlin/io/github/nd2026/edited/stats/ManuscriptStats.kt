package io.github.nd2026.edited.stats

import io.github.nd2026.edited.core.Sentences
import io.github.nd2026.edited.project.ChapterTitle
import io.github.nd2026.edited.project.Project
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.ceil

data class ChapterStats(val path: Path, val title: String, val chars: Int, val charsNoSpace: Int, val sentences: Int)

/**
 * Length statistics as web-novel platforms count them: heading lines (`# 제1화`) are not part of
 * the body, line breaks are never counted, and "공백 제외" also drops spaces and tabs. Characters
 * are counted as Unicode code points, so an emoji counts once.
 */
object ManuscriptStats {
    /** Characters per page of standard 200-character Korean manuscript paper (원고지). */
    const val MANUSCRIPT_PAGE_CHARS = 200

    fun of(project: Project): List<ChapterStats> = project.chapters().map { of(it.path, Files.readString(it.path)) }

    fun of(path: Path, text: String): ChapterStats {
        val body = text.lineSequence().filterNot(ChapterTitle::isHeading).toList()
        var chars = 0
        var noSpace = 0
        for (line in body) {
            line.codePoints().forEach { cp ->
                if (cp == '\r'.code) return@forEach
                chars++
                if (!Character.isWhitespace(cp)) noSpace++
            }
        }
        return ChapterStats(path, ChapterTitle.of(path, text), chars, noSpace, Sentences.split(body.joinToString("\n")).size)
    }

    fun total(chapters: List<ChapterStats>): ChapterStats = ChapterStats(
        path = Path.of(""),
        title = "합계",
        chars = chapters.sumOf { it.chars },
        charsNoSpace = chapters.sumOf { it.charsNoSpace },
        sentences = chapters.sumOf { it.sentences },
    )

    /** 원고지 매수 (spaces included, rounded up). */
    fun manuscriptPages(chars: Int): Int = ceil(chars / MANUSCRIPT_PAGE_CHARS.toDouble()).toInt()
}
