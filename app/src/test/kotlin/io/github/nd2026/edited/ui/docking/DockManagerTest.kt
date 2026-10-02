package io.github.nd2026.edited.ui.docking

import java.awt.Rectangle
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DockManagerTest {
    private val p = DefaultDockLayout

    @Test
    fun defaultLayoutHasExpectedEdges() {
        val state = p.state()
        val groups = state.root.tabGroups().map { it.paneIds }
        assertEquals(listOf(p.LEFT, listOf(p.EDITOR), p.RIGHT, p.BOTTOM), groups)
        assertEquals(p.EDITOR, state.focusedPane)
    }

    @Test
    fun hideAndRestoreRoundTrip() {
        val manager = DockManager(p.state())
        manager.apply(DockCommand.HidePane(p.STRUCTURE, DockEdge.LEFT))
        assertEquals(listOf(p.STRUCTURE), manager.state.hidden[DockEdge.LEFT])
        assertTrue(p.STRUCTURE !in manager.state.root.paneIds())

        manager.apply(DockCommand.RestorePane(p.STRUCTURE, p.PROJECT))
        assertTrue(manager.state.hidden.isEmpty())
        assertEquals(listOf(p.PROJECT, p.STRUCTURE), manager.state.root.tabsOf(p.PROJECT)!!.paneIds)
    }

    @Test
    fun hidingLastPaneOfGroupCollapsesSplit() {
        val manager = DockManager(p.state())
        p.RIGHT.forEach { manager.apply(DockCommand.HidePane(it, DockEdge.RIGHT)) }
        assertEquals(3, manager.state.root.tabGroups().size)
        assertEquals(p.RIGHT, manager.state.hidden[DockEdge.RIGHT])
    }

    @Test
    fun floatUpdateBoundsAndDockToEdge() {
        val manager = DockManager(p.state())
        manager.apply(DockCommand.FloatPane(p.GRAPH, Rectangle(10, 20, 300, 200)))
        manager.apply(DockCommand.SetFloatingBounds(p.GRAPH, Rectangle(50, 60, 400, 300)))
        assertEquals(Rectangle(50, 60, 400, 300), manager.state.floating.single().bounds)

        manager.apply(DockCommand.DockToEdge(p.GRAPH, DockEdge.RIGHT))
        val root = manager.state.root as DockNode.Split
        assertEquals(DockNode.Tabs(listOf(p.GRAPH)), root.second)
        assertTrue(manager.state.floating.isEmpty())
    }

    @Test
    fun focusActivatesTab() {
        val manager = DockManager(p.state())
        manager.apply(DockCommand.FocusPane(p.SEARCH))
        assertEquals(p.SEARCH, manager.state.root.tabsOf(p.SEARCH)!!.active)
        assertEquals(p.SEARCH, manager.state.focusedPane)
    }

    @Test
    fun failedCommandKeepsStateAndDoesNotNotify() {
        val manager = DockManager(p.state())
        var notifications = 0
        manager.addListener { notifications++ }
        val before = manager.state
        assertFailsWith<IllegalArgumentException> { manager.apply(DockCommand.MovePane(PaneId("nope"), p.EDITOR, DockDrop.LEFT)) }
        assertEquals(before, manager.state)
        assertEquals(0, notifications)
        manager.apply(DockCommand.FocusPane(p.EDITOR)) // no-op: already focused
        assertEquals(0, notifications)
        manager.apply(DockCommand.FocusPane(p.GRAPH))
        assertEquals(1, notifications)
    }

    @Test
    fun randomCommandSequencesKeepInvariants() {
        val all = p.state().allPaneIds()
        val random = Random(42)
        repeat(20) {
            val manager = DockManager(p.state())
            repeat(200) {
                val pane = all.random(random)
                val docked = manager.state.root.paneIds()
                val command = when (random.nextInt(8)) {
                    0 -> docked.filter { it != pane }.randomOrNull(random)
                        ?.let { DockCommand.MovePane(pane, it, DockDrop.entries.random(random)) }
                    1 -> DockCommand.HidePane(pane, DockEdge.entries.random(random))
                    2 -> manager.state.hidden.values.flatten().randomOrNull(random)
                        ?.let { DockCommand.RestorePane(it, docked.randomOrNull(random), DockDrop.entries.random(random)) }
                    3 -> DockCommand.FloatPane(pane, Rectangle(0, 0, 100, 100))
                    4 -> DockCommand.DockToEdge(pane, DockEdge.entries.random(random))
                    5 -> DockCommand.FocusPane(pane)
                    6 -> (manager.state.root as? DockNode.Split)?.let { DockCommand.ResizeSplit(emptyList(), random.nextFloat()) }
                    else -> DockCommand.ReplaceLayout(p.state())
                } ?: return@repeat
                runCatching { manager.apply(command) }
                val panes = manager.state.allPaneIds()
                assertEquals(all.toSet(), panes.toSet(), "every pane must stay somewhere after $command")
                assertEquals(panes.size, panes.toSet().size, "no pane may be duplicated after $command")
                assertNoEmptySplit(manager.state.root)
            }
        }
    }

    private fun assertNoEmptySplit(node: DockNode) {
        if (node !is DockNode.Split) return
        assertTrue(node.first != DockNode.Empty && node.second != DockNode.Empty)
        assertTrue(node.ratio in DockManager.MIN_RATIO..DockManager.MAX_RATIO || node.ratio in 0.05f..0.95f)
        assertNoEmptySplit(node.first)
        assertNoEmptySplit(node.second)
    }
}
