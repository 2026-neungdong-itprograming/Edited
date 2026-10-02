package io.github.nd2026.edited.project

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProjectServiceTest {
    private fun tempDir() = Files.createTempDirectory("texted-test").also { it.toFile().deleteOnExit() }

    @Test
    fun createsStructureAndReopens() {
        val parent = tempDir()
        val created = ProjectService.create(NewProjectRequest("달빛 아래의 계약", parent.toString(), "작가", initGit = false))

        val root = parent.resolve("달빛 아래의 계약")
        assertTrue(Project.isProject(root))
        assertTrue(Files.isDirectory(created.project.manuscriptDir))
        assertTrue(Files.isRegularFile(created.project.userDictionary))
        assertEquals(listOf(ProjectService.FIRST_CHAPTER), created.project.chapters().map { it.name })

        val reopened = Project.open(root)
        assertEquals("달빛 아래의 계약", reopened.title)
        assertEquals("작가", reopened.manifest.author)
    }

    @Test
    fun initialisesGitRepositoryWithInitialCommit() {
        val parent = tempDir()
        val created = ProjectService.create(NewProjectRequest("Git 작품", parent.toString()))
        assertTrue(created.warnings.isEmpty(), created.warnings.toString())
        assertTrue(Files.isDirectory(created.project.root.resolve(".git")))
        // Managed folders stay out of the project tree.
        assertTrue(created.project.tree().children.none { it.name == ".git" })
    }

    @Test
    fun validatesInput() {
        val parent = tempDir()
        assertNotNull(ProjectService.validate(NewProjectRequest("  ", parent.toString())))
        assertNotNull(ProjectService.validate(NewProjectRequest("a", " ")))
        assertNotNull(ProjectService.validate(NewProjectRequest("CON", parent.toString())))
        assertNull(ProjectService.validate(NewProjectRequest("정상 제목", parent.toString())))

        ProjectService.create(NewProjectRequest("중복", parent.toString(), initGit = false))
        assertNotNull(ProjectService.validate(NewProjectRequest("중복", parent.toString())))
    }

    @Test
    fun sanitisesFolderName() {
        assertEquals("a_b_c", ProjectService.folderNameFor(" a:b?c. "))
    }

    @Test
    fun treeSortsNaturally() {
        assertTrue(ProjectNode.compareNatural("2.md", "10.md") < 0)
        assertTrue(ProjectNode.compareNatural("a1", "A2") < 0)
    }
}
