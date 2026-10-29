package io.github.nd2026.edited.stats

import io.github.nd2026.edited.project.NewProjectRequest
import io.github.nd2026.edited.project.ProjectService
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class ManuscriptStatsTest {
    private val path = Path.of("01. 제1화.md")

    @Test
    fun countsBodyWithoutHeadingOrLineBreaks() {
        val stats = ManuscriptStats.of(path, "# 제1화\n\n가나 다.\r\n라마!\n")
        assertEquals("제1화", stats.title)
        assertEquals(8, stats.chars)        // "가나 다." + "라마!"
        assertEquals(7, stats.charsNoSpace)
        assertEquals(2, stats.sentences)
    }

    @Test
    fun emptyAndBlankFiles() {
        assertEquals(0, ManuscriptStats.of(path, "").chars)
        val blank = ManuscriptStats.of(path, "\n\n\n")
        assertEquals(0, blank.chars)
        assertEquals(0, blank.sentences)
        assertEquals("01. 제1화", blank.title)
    }

    @Test
    fun mixedScriptsAndEmojiCountAsCodePoints() {
        val stats = ManuscriptStats.of(path, "한글 abc 😀")
        assertEquals(8, stats.chars)
        assertEquals(6, stats.charsNoSpace)
    }

    @Test
    fun manuscriptPagesRoundUp() {
        assertEquals(0, ManuscriptStats.manuscriptPages(0))
        assertEquals(1, ManuscriptStats.manuscriptPages(1))
        assertEquals(2, ManuscriptStats.manuscriptPages(201))
    }

    @Test
    fun projectTotals() {
        val project = ProjectService.create(NewProjectRequest("통계", Files.createTempDirectory("texted-stats").toString(), initGit = false)).project
        Files.writeString(project.manuscriptDir.resolve("02. 제2화.md"), "# 제2화\n둘째.")
        val chapters = ManuscriptStats.of(project)
        assertEquals(listOf("제1화", "제2화"), chapters.map { it.title })
        assertEquals(3, ManuscriptStats.total(chapters).chars)
    }
}
