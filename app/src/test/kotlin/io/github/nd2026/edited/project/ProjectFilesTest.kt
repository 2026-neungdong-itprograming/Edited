package io.github.nd2026.edited.project

import io.github.nd2026.edited.world.CharacterRepository
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProjectFilesTest {
    private fun project() = ProjectService.create(NewProjectRequest("작품", Files.createTempDirectory("texted-files").toString(), initGit = false)).project

    @Test
    fun newProjectsStartWithAnEmptyEpisode() {
        val project = project()
        val first = project.chapters().single()
        assertEquals("01. 제1화.episode", first.name)
        assertEquals("", Files.readString(first.path))
        assertEquals("제1화", ChapterTitle.of(first.path, ""))
    }

    @Test
    fun episodesAreNumberedAfterTheLastOne() {
        val project = project()
        assertEquals("제2화", ProjectFiles.nextEpisodeTitle(project.manuscriptDir))
        val created = ProjectFiles.create(project, NovelFormat.EPISODE, project.manuscriptDir, "낯선 손님")
        assertEquals("02. 낯선 손님.episode", created.fileName.toString())
        assertEquals(listOf("01. 제1화.episode", "02. 낯선 손님.episode"), project.chapters().map { it.name })
        // The title comes from the file name, so a line starting with "#" is just prose.
        assertEquals("낯선 손님", ChapterTitle.of(created, "# 이건 본문\n"))
    }

    @Test
    fun sheetsGoToTheirOwnFolderWhereverTheMenuWasOpened() {
        val project = project()
        val chapter = project.chapters().single().path
        val dir = ProjectFiles.folderFor(project, NovelFormat.CHARACTER, chapter)
        assertEquals(project.charactersDir.toAbsolutePath().normalize(), dir)
        val sheet = ProjectFiles.create(project, NovelFormat.CHARACTER, dir, "한서연")
        Files.writeString(sheet, "별칭: 서연, 한 대리\n27세. 주인공의 직장 동료.\n")

        val loaded = CharacterRepository.load(project).single()
        assertEquals("한서연", loaded.name)
        assertEquals(listOf("서연", "한 대리"), loaded.aliases)
        assertEquals("27세. 주인공의 직장 동료.", loaded.description)
    }

    @Test
    fun subfoldersOfTheHomeFolderAreKept() {
        val project = project()
        val arc = ProjectFiles.createFolder(project, project.manuscriptDir, "1부")
        assertEquals(arc, ProjectFiles.folderFor(project, NovelFormat.EPISODE, arc))
        assertEquals("01. 제1화.episode", ProjectFiles.create(project, NovelFormat.EPISODE, arc, "제1화").fileName.toString())
    }

    @Test
    fun renameAndChangeExtensionKeepTheContent() {
        val project = project()
        val legacy = project.manuscriptDir.resolve("02. 옛 원고.md")
        Files.writeString(legacy, "본문")

        val renamed = ProjectFiles.rename(project, legacy, "02. 새 원고.md")
        assertFalse(Files.exists(legacy))
        val converted = ProjectFiles.changeExtension(project, renamed, "episode")
        assertEquals("02. 새 원고.episode", converted.fileName.toString())
        assertEquals("본문", Files.readString(converted))
        assertEquals("새 원고", ChapterTitle.of(converted, "본문"))
    }

    @Test
    fun refusesClashesBadNamesAndEscapes() {
        val project = project()
        val first = project.chapters().single().path
        val second = ProjectFiles.create(project, NovelFormat.EPISODE, project.manuscriptDir, "둘째")
        assertFailsWith<IllegalArgumentException> { ProjectFiles.rename(project, second, first.fileName.toString()) }
        assertFailsWith<IllegalArgumentException> { ProjectFiles.rename(project, second, "a/b.episode") }
        assertFailsWith<IllegalArgumentException> { ProjectFiles.rename(project, second, "..") }
        assertFailsWith<IllegalArgumentException> { ProjectFiles.rename(project, project.root, "다른 이름") }
        assertNotNull(ProjectFiles.validateName("  "))
        assertNotNull(ProjectFiles.validateName("CON"))
        assertNull(ProjectFiles.validateName("03. 비 오는 밤"))
        assertTrue(Files.exists(second))
    }
}
