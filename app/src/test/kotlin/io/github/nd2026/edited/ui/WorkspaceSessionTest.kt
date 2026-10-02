package io.github.nd2026.edited.ui

import io.github.nd2026.edited.project.NewProjectRequest
import io.github.nd2026.edited.project.ProjectService
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkspaceSessionTest {
    @Test
    fun opensEditsAndSavesARealProjectFile() {
        val parent = Files.createTempDirectory("texted-workspace-test")
        val project = ProjectService.create(NewProjectRequest("작품", parent.toString(), initGit = false)).project
        val session = WorkspaceSession(project)
        val document = session.activeDocument!!

        document.textArea.typeAtCaret("새 문장")
        assertTrue(document.dirty)
        assertTrue(session.saveActive())
        assertFalse(document.dirty)
        assertTrue(Files.readString(document.path).startsWith("새 문장"))
    }

    @Test
    fun tracksMultipleOpenDocumentsAndSelection() {
        val parent = Files.createTempDirectory("texted-workspace-test")
        val project = ProjectService.create(NewProjectRequest("작품", parent.toString(), initGit = false)).project
        val second = project.manuscriptDir.resolve("02. 다음 화.md")
        Files.writeString(second, "# 다음 화")
        val session = WorkspaceSession(project)

        session.open(second)
        assertEquals(2, session.openDocuments.size)
        assertEquals(second, session.activePath)
        assertTrue(session.select(project.chapters().first().path))
    }
}
