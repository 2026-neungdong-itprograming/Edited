package io.github.nd2026.edited.ui.docking

import io.github.nd2026.edited.event.InputEvent
import io.github.nd2026.edited.theme.Theme
import io.github.nd2026.edited.ui.OverlayHostWidget
import io.github.nd2026.edited.ui.RootPane
import io.github.nd2026.edited.ui.Widget
import java.awt.Rectangle
import java.awt.event.KeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DockHostWidgetTest {
    private val p = DefaultDockLayout

    private class Content(val id: PaneId) : Widget() {
        init {
            focusable = true
        }
    }

    private class FakeWindow(override val paneId: PaneId, var b: Rectangle, val pane: DockPaneWidget) : FloatingPaneWindow {
        var closed = false
        override val bounds: Rectangle get() = b
        override fun moveTo(bounds: Rectangle) { b = bounds }
        override fun applyTheme(theme: Theme) {}
        override fun refresh() {}
        override fun toFront() {}
        override fun showContextMenu() = false
        override fun close() { closed = true; pane.release() }
    }

    private val contents = mutableMapOf<PaneId, Content>()

    private fun host(state: DockLayoutState = p.state()): DockHostWidget {
        val registry = PaneRegistry(p.state().root.paneIds().map { id ->
            PaneDescriptor(id, id.value, defaultEdge = p.edges[id], content = Content(id).also { contents[id] = it })
        })
        return DockHostWidget(DockManager(state), registry).apply {
            floatingWindowFactory = FloatingWindowFactory { host, d, b ->
                FakeWindow(d.id, b, DockPaneWidget(host, DockNode.Tabs(listOf(d.id)), floating = true))
            }
            floatingWindowsEnabled = true
            setBounds(0, 0, 1200, 800)
        }
    }

    @Test
    fun rendersOnePaneWidgetPerGroupWithActiveContent() {
        val host = host()
        val panes = host.children.filterIsInstance<DockPaneWidget>()
        assertEquals(4, panes.size)
        assertEquals(3, host.children.filterIsInstance<DockSplitterWidget>().size)
        for (id in listOf(p.PROJECT, p.EDITOR, p.CHARACTERS, p.PROBLEMS)) {
            assertNotNull(contents.getValue(id).parent, "$id should be shown")
        }
        assertNull(contents.getValue(p.STRUCTURE).parent)
        val left = host.boundsOf(p.PROJECT)!!
        val editor = host.boundsOf(p.EDITOR)!!
        val bottom = host.boundsOf(p.PROBLEMS)!!
        assertTrue(left.x < editor.x && editor.y + editor.height <= bottom.y)
    }

    @Test
    fun hideShowsStripAndRestoreReturnsToDefaultGroup() {
        val host = host()
        host.hidePane(p.STRUCTURE)
        host.hidePane(p.PROJECT)
        val strip = host.children.filterIsInstance<ToolWindowStripWidget>().first { it.edge == DockEdge.LEFT }
        assertEquals(listOf(p.STRUCTURE, p.PROJECT), strip.items)
        assertTrue(strip.visible && strip.bounds.width > 0)

        // The left group is gone, so restoring docks along the left edge.
        host.restorePane(p.PROJECT)
        assertEquals(DockNode.Tabs(listOf(p.PROJECT)), (host.state.root as DockNode.Split).first)
        host.restorePane(p.STRUCTURE)
        assertEquals(listOf(p.PROJECT, p.STRUCTURE), host.state.root.tabsOf(p.PROJECT)!!.paneIds)
        assertFalse(strip.visible)
    }

    @Test
    fun editorCannotBeHidden() {
        val host = host()
        assertFalse(host.canHide(p.EDITOR))
        host.hidePane(p.EDITOR)
        assertTrue(p.EDITOR in host.state.root.paneIds())
    }

    @Test
    fun floatingMovesContentIntoWindowAndBack() {
        val host = host()
        host.floatPane(p.GRAPH, Rectangle(100, 100, 400, 300))
        val window = host.openFloatingWindows.getValue(p.GRAPH) as FakeWindow
        assertSame(window.pane, contents.getValue(p.GRAPH).parent)

        host.onFloatingWindowMoved(p.GRAPH, Rectangle(120, 130, 500, 300))
        assertEquals(Rectangle(120, 130, 500, 300), host.state.floating.single().bounds)

        host.dockPane(p.GRAPH)
        assertTrue(window.closed)
        assertTrue(host.openFloatingWindows.isEmpty())
        assertEquals(p.RIGHT, host.state.root.tabsOf(p.GRAPH)!!.paneIds.sortedBy { p.RIGHT.indexOf(it) })
        assertTrue(contents.getValue(p.GRAPH).parent is DockPaneWidget)
    }

    @Test
    fun floatingWindowsWaitUntilEnabled() {
        val manager = DockManager(p.state())
        manager.apply(DockCommand.FloatPane(p.SEARCH, Rectangle(0, 0, 300, 200)))
        val host = host(manager.state).apply { floatingWindowsEnabled = false }
        assertTrue(host.openFloatingWindows.isEmpty())
        host.floatingWindowsEnabled = true
        assertEquals(setOf(p.SEARCH), host.openFloatingWindows.keys)
    }

    @Test
    fun keyboardMovesPaneToNeighbourAndCyclesFocus() {
        val host = host()
        val root = RootPane()
        host.install(root)
        host.focusPane(p.STRUCTURE)
        assertTrue(root.keymap.dispatch(InputEvent.KeyPressed(KeyEvent.VK_RIGHT, KeyEvent.ALT_DOWN_MASK or KeyEvent.SHIFT_DOWN_MASK)))
        assertEquals(listOf(p.EDITOR, p.STRUCTURE), host.state.root.tabsOf(p.EDITOR)!!.paneIds)
        assertEquals(p.STRUCTURE, host.state.focusedPane)

        host.focusPane(p.PROJECT)
        root.keymap.dispatch(InputEvent.KeyPressed(KeyEvent.VK_F6, 0))
        assertEquals(p.STRUCTURE, host.state.focusedPane) // the editor group's active tab

        root.keymap.dispatch(InputEvent.KeyPressed(KeyEvent.VK_ESCAPE, KeyEvent.SHIFT_DOWN_MASK))
        assertEquals(DockEdge.LEFT, host.state.hiddenEdgeOf(p.STRUCTURE))

        root.keymap.dispatch(InputEvent.KeyPressed(KeyEvent.VK_R, KeyEvent.ALT_DOWN_MASK or KeyEvent.SHIFT_DOWN_MASK))
        assertEquals(p.state().root, host.state.root)
    }

    @Test
    fun movingDownFromBottomGroupDocksAlongEdgeOnlyWhenItChangesLayout() {
        val host = host()
        assertTrue(host.canMove(p.SEARCH, DockEdge.TOP))
        host.movePane(p.SEARCH, DockEdge.TOP)
        // Search was below the editor/right columns; the nearest group above it gets it.
        assertTrue(p.SEARCH in host.state.root.tabGroups().first { p.SEARCH in it.paneIds }.paneIds)
        assertTrue(host.state.root.tabsOf(p.SEARCH)!!.paneIds.size > 1)

        val single = host(DockLayoutState(DockNode.Tabs(listOf(p.EDITOR)), focusedPane = p.EDITOR))
        assertFalse(single.canMove(p.EDITOR, DockEdge.LEFT))
    }

    @Test
    fun contextMenuOffersDockCommands() {
        val host = host()
        val overlay = OverlayHostWidget(host).apply { setBounds(0, 0, 1200, 800) }
        val menu = host.showContextMenu(p.PROBLEMS, overlay, 10, 10)
        assertTrue("플로팅" in menu.labels && "숨기기" in menu.labels && "레이아웃 초기화" in menu.labels)
        assertTrue(menu.activate("오른쪽으로 분할"))
        val split = host.state.root.tabsOf(p.PROBLEMS)!!
        assertEquals(listOf(p.PROBLEMS), split.paneIds)

        val editorMenu = host.showContextMenu(p.EDITOR, overlay, 10, 10)
        assertFalse(editorMenu.activate("숨기기"))
        assertFalse(editorMenu.activate("닫기"))
    }

    @Test
    fun splitterDragChangesRatioWithinMinimumSizes() {
        val host = host()
        val rootSplitter = host.children.filterIsInstance<DockSplitterWidget>().first { it.path.isEmpty() }
        val before = host.children.filterIsInstance<DockPaneWidget>()
        assertTrue(rootSplitter.onMouseEvent(InputEvent.MousePressed(10, rootSplitter.bounds.y + 3, 1, 0)))
        rootSplitter.onMouseEvent(InputEvent.MouseDragged(10, 400, 0))
        assertEquals(0.5f, (host.state.root as DockNode.Split).ratio, 0.01f)
        rootSplitter.onMouseEvent(InputEvent.MouseDragged(10, 5000, 0))
        val bottom = host.boundsOf(p.PROBLEMS)!!
        assertEquals(DockHostWidget.MIN_PANE_SIZE, bottom.height)
        // A ratio-only change reuses the pane widgets instead of rebuilding them.
        assertEquals(before, host.children.filterIsInstance<DockPaneWidget>())
    }

    @Test
    fun clickingATabActivatesIt() {
        val host = host()
        val bottom = host.children.filterIsInstance<DockPaneWidget>().first { p.PROBLEMS in it.group.paneIds }
        val searchTab = bottom.tabRects().first { it.first == p.SEARCH }.second
        bottom.onMouseEvent(InputEvent.MousePressed(bottom.bounds.x + searchTab.x + 2, bottom.bounds.y + 5, 1, 0))
        assertEquals(p.SEARCH, host.state.root.tabsOf(p.SEARCH)!!.active)
        assertSame(bottom, contents.getValue(p.SEARCH).parent)
        assertNull(contents.getValue(p.PROBLEMS).parent)
    }
}
