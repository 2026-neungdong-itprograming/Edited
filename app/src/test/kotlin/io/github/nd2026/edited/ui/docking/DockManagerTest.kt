package io.github.nd2026.edited.ui.docking

import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DockManagerTest {
    private val project = PaneId("project")
    private val editor = PaneId("editor")
    private val problems = PaneId("problems")

    @Test
    fun `center drop merges panes into a tab group`() {
        val manager = managerWithThreePanes()

        manager.apply(DockCommand.MovePane(project, editor, DockDrop.CENTER, tabIndex = 0))

        val root = assertIs<DockNode.Tabs>(manager.state.root)
        assertEquals(listOf(project, editor, problems), root.paneIds)
        assertEquals(project, root.active)
        assertUnique(manager.state)
    }

    @Test
    fun `edge drop creates a split and removing its last tab collapses it`() {
        val manager = managerWithThreePanes()
        manager.apply(DockCommand.MovePane(problems, editor, DockDrop.BOTTOM))

        val split = assertIs<DockNode.Split>(manager.state.root)
        assertEquals(Axis.VERTICAL, split.axis)
        assertEquals(listOf(project, editor), split.first.paneIds())
        assertEquals(listOf(problems), split.second.paneIds())

        manager.apply(DockCommand.ClosePane(problems))

        assertIs<DockNode.Tabs>(manager.state.root)
        assertEquals(listOf(project, editor), manager.state.root.paneIds())
        assertUnique(manager.state)
    }

    @Test
    fun `hidden and floating panes can be restored without duplication`() {
        val manager = managerWithThreePanes()
        manager.apply(DockCommand.HidePane(project, DockEdge.LEFT))
        assertEquals(listOf(project), manager.state.hidden[DockEdge.LEFT])

        manager.apply(DockCommand.RestorePane(project, editor, DockDrop.LEFT))
        manager.apply(DockCommand.FloatPane(problems, Rectangle(20, 30, 640, 480)))
        assertEquals(problems, manager.state.floating.single().paneId)

        manager.apply(DockCommand.RestorePane(problems, editor, DockDrop.CENTER))

        assertTrue(manager.state.floating.isEmpty())
        assertUnique(manager.state)
        assertEquals(setOf(project, editor, problems), manager.state.allPaneIds().toSet())
    }

    @Test
    fun `split ratio is clamped and invalid path is rejected`() {
        val manager = managerWithThreePanes()
        manager.apply(DockCommand.MovePane(problems, editor, DockDrop.RIGHT))
        manager.apply(DockCommand.ResizeSplit(emptyList(), 2f))

        assertEquals(0.95f, assertIs<DockNode.Split>(manager.state.root).ratio)
        assertFailsWith<IllegalArgumentException> {
            manager.apply(DockCommand.ResizeSplit(listOf(2), 0.5f))
        }
    }

    @Test
    fun `duplicate panes in separate locations are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            DockManager(
                DockLayoutState(
                    root = DockNode.Tabs(listOf(editor)),
                    hidden = mapOf(DockEdge.LEFT to listOf(editor)),
                ),
            )
        }
    }

    private fun managerWithThreePanes() = DockManager(
        DockLayoutState(root = DockNode.Tabs(listOf(project, editor, problems), editor)),
    )

    private fun assertUnique(state: DockLayoutState) {
        val ids = state.allPaneIds()
        assertEquals(ids.distinct().size, ids.size)
    }
}
