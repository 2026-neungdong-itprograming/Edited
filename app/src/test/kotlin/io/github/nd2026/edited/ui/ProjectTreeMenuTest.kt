package io.github.nd2026.edited.ui

import io.github.nd2026.edited.event.InputEvent
import io.github.nd2026.edited.project.NewProjectRequest
import io.github.nd2026.edited.project.ProjectFiles
import io.github.nd2026.edited.project.ProjectService
import io.github.nd2026.edited.ui.components.ContextMenuItem
import io.github.nd2026.edited.ui.components.ContextMenuWidget
import io.github.nd2026.edited.ui.components.DialogWidget
import java.awt.event.MouseEvent
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProjectTreeMenuTest {
    private fun OverlayHostWidget.menus() = children.filterIsInstance<ContextMenuWidget>()

    private fun rowMiddle(menu: ContextMenuWidget, row: Int) = menu.bounds.y + 6 + row * ContextMenuWidget.ROW_HEIGHT + ContextMenuWidget.ROW_HEIGHT / 2

    @Test
    fun rightClickOpensTheFileMenuAndNewCascades() {
        val project = ProjectService.create(NewProjectRequest("메뉴", Files.createTempDirectory("texted-menu").toString(), initGit = false)).project
        val workspace = ProjectWorkspaceWidget(WorkspaceSession(project))
        val overlay = OverlayHostWidget(workspace).apply { setBounds(0, 0, 1280, 800) }

        // Project pane: x 52.., list starts 44 px below its top (76); row 0 is the project root.
        val x = 100
        val y = 76 + 44 + 12
        overlay.hitTest(x, y)!!.onMouseEvent(InputEvent.MousePressed(x, y, MouseEvent.BUTTON3, 0))
        val menu = overlay.menus().single()

        // Hovering "새로 만들기" (row 0) opens the submenu beside it.
        menu.onMouseEvent(InputEvent.MouseMoved(menu.bounds.x + 20, rowMiddle(menu, 0), 0))
        val submenu = assertNotNull(menu.openSubmenu)
        assertEquals(2, overlay.menus().size)
        assertTrue(submenu.bounds.x >= menu.bounds.x + menu.bounds.width - 4)

        // "이름 바꾸기" (row 1) closes it again.
        menu.onMouseEvent(InputEvent.MouseMoved(menu.bounds.x + 20, rowMiddle(menu, 1), 0))
        assertNull(menu.openSubmenu)

        // Re-open and pick "캐릭터" (row 1 of the submenu): the whole chain closes and the name dialog opens.
        menu.onMouseEvent(InputEvent.MouseMoved(menu.bounds.x + 20, rowMiddle(menu, 0), 0))
        val again = assertNotNull(menu.openSubmenu)
        val pickY = rowMiddle(again, 1)
        again.onMouseEvent(InputEvent.MousePressed(again.bounds.x + 20, pickY, MouseEvent.BUTTON1, 0))
        again.onMouseEvent(InputEvent.MouseReleased(again.bounds.x + 20, pickY, MouseEvent.BUTTON1, 0))
        assertTrue(overlay.menus().isEmpty())
        assertEquals(1, overlay.children.filterIsInstance<DialogWidget>().size)
    }

    @Test
    fun submenuItemsAreBuiltFromTheFactory() {
        var ran = false
        val item = ContextMenuItem.submenu("새로 만들기", listOf(ContextMenuItem("에피소드") { ran = true }))
        assertEquals(1, item.submenu.size)
        item.submenu.single().action()
        assertTrue(ran && item.enabled)
        assertTrue(!ContextMenuItem.submenu("빈 메뉴", listOf(ContextMenuItem("x", enabled = false) {})).enabled)
    }

    @Test
    fun renamingAnOpenChapterKeepsItsTab() {
        val project = ProjectService.create(NewProjectRequest("탭", Files.createTempDirectory("texted-menu").toString(), initGit = false)).project
        val session = WorkspaceSession(project)
        val before = session.activePath!!
        session.activeDocument!!.textArea.typeAtCaret("고친 문장")
        session.saveAll()

        val after = ProjectFiles.rename(project, before, "01. 프롤로그.episode")
        session.moved(before, after)
        assertEquals(after, session.activePath)
        assertEquals(listOf(after), session.openDocuments.map { it.path })
        assertEquals("고친 문장", session.activeDocument!!.textArea.snapshot())
    }
}
