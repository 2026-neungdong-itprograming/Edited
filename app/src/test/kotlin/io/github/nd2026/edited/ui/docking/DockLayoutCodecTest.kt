package io.github.nd2026.edited.ui.docking

import io.github.nd2026.edited.ui.Widget
import java.awt.Rectangle
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DockLayoutCodecTest {
    private val p = DefaultDockLayout

    private fun registry(vararg extra: PaneDescriptor): PaneRegistry {
        val descriptors = p.state().root.paneIds().map { id ->
            PaneDescriptor(id, id.value, defaultEdge = p.edges[id], content = object : Widget() {})
        }
        return PaneRegistry(descriptors + extra)
    }

    @Test
    fun roundTripsEveryField() {
        val manager = DockManager(p.state())
        manager.apply(DockCommand.HidePane(p.STRUCTURE, DockEdge.LEFT))
        manager.apply(DockCommand.FloatPane(p.GRAPH, Rectangle(10, 20, 300, 200)))
        manager.apply(DockCommand.ResizeSplit(listOf(0), 0.33f))
        manager.apply(DockCommand.FocusPane(p.SEARCH))
        val state = manager.state

        val json = DockLayoutCodec.encode(state)
        assertTrue("\"schemaVersion\": 1" in json, json)
        assertEquals(state, DockLayoutCodec.decode(json))
    }

    @Test
    fun rejectsCorruptAndNewerLayouts() {
        assertNull(DockLayoutCodec.decode("{not json"))
        assertNull(DockLayoutCodec.decode("""{"root":{"type":"empty"}}"""))
        assertNull(DockLayoutCodec.decode("""{"schemaVersion":99,"root":{"type":"empty"}}"""))
    }

    @Test
    fun repairsDuplicatesAndBadRatios() {
        val decoded = DockLayoutCodec.decode(
            """{"schemaVersion":1,"root":{"type":"split","axis":"HORIZONTAL","ratio":7,
               "first":{"type":"tabs","panes":["editor","editor"],"active":"missing"},
               "second":{"type":"tabs","panes":["editor"]}}}""",
        )!!
        assertEquals(DockNode.Tabs(listOf(p.EDITOR)), decoded.root)
    }

    @Test
    fun reconcileDropsUnknownAndMergesMissingPanes() {
        val saved = DockLayoutState(
            root = DockNode.Split(
                Axis.HORIZONTAL, 0.25f,
                DockNode.Tabs(listOf(p.PROJECT, PaneId("removed-plugin"))),
                DockNode.Tabs(listOf(p.EDITOR)),
            ),
            hidden = mapOf(DockEdge.BOTTOM to listOf(p.PROBLEMS)),
            focusedPane = p.EDITOR,
        )
        val newPane = PaneDescriptor(PaneId("outline"), "Outline", defaultEdge = DockEdge.RIGHT, content = object : Widget() {})
        val state = DockLayoutReconciler.reconcile(saved, registry(newPane), p.state())

        val ids = state.allPaneIds()
        assertTrue(PaneId("removed-plugin") !in ids)
        assertEquals((registry(newPane).ids).toSet(), ids.toSet())
        assertEquals(ids.size, ids.toSet().size)
        // Hidden panes stay hidden; Structure joins Project's group like in the default layout.
        assertEquals(listOf(p.PROBLEMS), state.hidden[DockEdge.BOTTOM])
        assertEquals(listOf(p.PROJECT, p.STRUCTURE), state.root.tabsOf(p.PROJECT)!!.paneIds)
        assertEquals(p.PROJECT, state.root.tabsOf(p.PROJECT)!!.active)
        // Search/Git History follow their default siblings' edge (Problems is hidden, so a new bottom group).
        assertEquals(state.root.tabsOf(p.SEARCH), state.root.tabsOf(p.GIT_HISTORY))
        assertEquals(p.EDITOR, state.focusedPane)
    }

    @Test
    fun storeFallsBackToNullForCorruptFiles() {
        val dir = Files.createTempDirectory("texted-layout").also { it.toFile().deleteOnExit() }
        val store = DockLayoutStore(dir.resolve("nested").resolve("layout.json"))
        val registry = registry()
        assertNull(store.load(registry, p.state()))

        val manager = DockManager(p.state())
        manager.apply(DockCommand.HidePane(p.WORLD, DockEdge.RIGHT))
        store.save(manager.state)
        assertEquals(manager.state, store.load(registry, p.state()))

        Files.writeString(store.path, "{\"schemaVersion\":1,\"root\":")
        assertNull(store.load(registry, p.state()))
    }
}
