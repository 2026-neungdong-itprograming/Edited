package io.github.nd2026.edited.ui

import io.github.nd2026.edited.event.InputEvent
import io.github.nd2026.edited.project.NewProjectRequest
import io.github.nd2026.edited.project.ProjectService
import io.github.nd2026.edited.vcs.HistoryService
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkspacePanesTest {
    private fun paint(w: Widget) {
        val image = BufferedImage(w.bounds.width, w.bounds.height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        w.paint(g, Rectangle(0, 0, w.bounds.width, w.bounds.height))
        g.dispose()
    }

    @Test
    fun everyLeftPanePaintsAndSavingRecordsHistory() {
        val project = ProjectService.create(NewProjectRequest("화면", Files.createTempDirectory("texted-panes").toString(), "작가")).project
        Files.writeString(project.charactersDir.resolve("a.md"), "# 서연\n별칭: 연\n주인공")
        val session = WorkspaceSession(project)
        val workspace = ProjectWorkspaceWidget(session).apply { setBounds(0, 0, 1280, 800) }

        for (index in 0..4) {
            workspace.showLeftPane(index)
            paint(workspace)
        }

        session.activeDocument!!.textArea.typeAtCaret("“서연이 왔다.\n")
        workspace.saveActiveDocument()
        assertEquals(2, HistoryService(project.root).log().size)
        workspace.showLeftPane(4)
        // Pick the newest version in the history pane and paint its sentence diff.
        workspace.onMouseEventAt(InputEvent.MousePressed(52 + 20, 76 + 50, 1, 0))
        paint(workspace)

        workspace.exportManuscript()
        assertTrue(Files.isRegularFile(project.root.resolve("export/화면.epub")))
    }

    private fun Widget.onMouseEventAt(event: InputEvent.MousePressed) = hitTest(event.x, event.y)?.onMouseEvent(event)
}
