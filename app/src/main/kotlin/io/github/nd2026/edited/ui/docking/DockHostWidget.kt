package io.github.nd2026.edited.ui.docking

import io.github.nd2026.edited.theme.ThemeChanged
import io.github.nd2026.edited.ui.Command
import io.github.nd2026.edited.ui.Container
import io.github.nd2026.edited.ui.KeyStroke
import io.github.nd2026.edited.ui.Keymap
import io.github.nd2026.edited.ui.OverlayHostWidget
import io.github.nd2026.edited.ui.RootPane
import io.github.nd2026.edited.ui.Widget
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.event.KeyEvent

/**
 * Renders a [DockManager]'s layout: one [DockPaneWidget] per tab group, a [DockSplitterWidget]
 * per split, [ToolWindowStripWidget]s for hidden panes and a [FloatingPaneWindow] per floating
 * pane. Widgets never edit the layout directly; every user action goes through a [DockCommand].
 *
 * The public command methods ([hidePane], [restorePane], [floatPane], [movePane], ...) are shared
 * by the pane header buttons, the [DockContextMenuWidget] and the keyboard bindings installed by
 * [install], so docking never depends on the mouse.
 */
class DockHostWidget(
    val manager: DockManager,
    val registry: PaneRegistry,
    private val defaultLayout: () -> DockLayoutState = DefaultDockLayout::state,
) : Container() {
    private val strips = mapOf(
        DockEdge.LEFT to ToolWindowStripWidget(this, DockEdge.LEFT),
        DockEdge.RIGHT to ToolWindowStripWidget(this, DockEdge.RIGHT),
        DockEdge.BOTTOM to ToolWindowStripWidget(this, DockEdge.BOTTOM),
    )
    private var paneWidgets: List<DockPaneWidget> = emptyList()
    private var splitters: Map<List<Int>, DockSplitterWidget> = emptyMap()
    private var shape: String? = null
    private val floatingWindows = linkedMapOf<PaneId, FloatingPaneWindow>()
    private var installedRoot: RootPane? = null
    private var unsubscribeTheme: (() -> Unit)? = null

    /** Last laid-out bounds of each tab group, used for keyboard neighbour navigation. */
    private var groupBounds: List<Pair<DockNode.Tabs, Rectangle>> = emptyList()

    /** Creates OS windows for floating panes; tests replace it with a headless fake. */
    var floatingWindowFactory: FloatingWindowFactory = FloatingWindowFactory(::FloatingDockWindow)

    /**
     * Floating windows are only opened once this is true, so a restored layout does not pop up
     * floating windows before the main window is shown.
     */
    var floatingWindowsEnabled: Boolean = false
        set(value) {
            field = value
            syncFloatingWindows()
        }

    val state: DockLayoutState get() = manager.state

    init {
        strips.values.forEach(::addChild)
        manager.addListener { onStateChanged() }
        onStateChanged()
    }

    // ---------------------------------------------------------------- commands

    /** Makes [paneId] the focused pane and the active tab of its group. */
    fun focusPane(paneId: PaneId) {
        if (paneId !in state.allPaneIds()) return
        manager.apply(DockCommand.FocusPane(paneId))
        val content = registry[paneId]?.content ?: return
        val pane = content.hostPane ?: return
        if (pane.focusManager.focused?.isInside(content) != true) firstFocusable(content)?.let(pane.focusManager::requestFocus)
        floatingWindows[paneId]?.toFront()
    }

    fun canHide(paneId: PaneId): Boolean =
        registry[paneId]?.hideable == true && state.hiddenEdgeOf(paneId) == null && paneId in state.allPaneIds()

    /** Hides [paneId] to the strip of its default edge. */
    fun hidePane(paneId: PaneId) {
        if (!canHide(paneId)) return
        val edge = registry.require(paneId).defaultEdge ?: DockEdge.LEFT
        val wasFocused = state.focusedPane == paneId
        manager.apply(DockCommand.HidePane(paneId, edge))
        if (wasFocused) state.root.paneIds().firstOrNull()?.let(::focusPane)
    }

    /**
     * Docks a hidden or floating pane back: into a group holding a pane from the same default
     * edge when one is docked, otherwise along that edge of the main layout.
     */
    fun restorePane(paneId: PaneId) {
        if (state.hiddenEdgeOf(paneId) == null && state.floating.none { it.paneId == paneId }) return
        val descriptor = registry.require(paneId)
        val edge = descriptor.defaultEdge
        val docked = state.root.paneIds()
        val defaultSiblings = defaultLayout().root.tabsOf(paneId)?.paneIds.orEmpty()
        val target = defaultSiblings.firstOrNull { it != paneId && it in docked }
            ?: docked.firstOrNull { registry[it]?.defaultEdge == edge }
        when {
            target != null -> manager.apply(DockCommand.RestorePane(paneId, target, DockDrop.CENTER))
            docked.isEmpty() || edge == null -> manager.apply(DockCommand.RestorePane(paneId))
            else -> manager.apply(DockCommand.DockToEdge(paneId, edge))
        }
        focusPane(paneId)
    }

    fun isFloating(paneId: PaneId): Boolean = state.floating.any { it.paneId == paneId }

    /** Moves [paneId] into its own floating window, by default where the pane is on screen now. */
    fun floatPane(paneId: PaneId, bounds: Rectangle? = null) {
        if (paneId !in state.allPaneIds() || isFloating(paneId)) return
        manager.apply(DockCommand.FloatPane(paneId, bounds ?: defaultFloatingBounds(paneId)))
    }

    /** Re-docks a floating pane. */
    fun dockPane(paneId: PaneId) = restorePane(paneId)

    fun canClose(paneId: PaneId): Boolean = registry[paneId]?.closable == true && paneId in state.allPaneIds()

    fun closePane(paneId: PaneId) {
        if (canClose(paneId)) manager.apply(DockCommand.ClosePane(paneId))
    }

    /** True if [movePane] would change the layout. */
    fun canMove(paneId: PaneId, direction: DockEdge): Boolean {
        val group = state.root.tabsOf(paneId) ?: return false
        return neighbour(group, direction) != null || state.root != group
    }

    /**
     * Keyboard docking: merges [paneId] into the neighbouring group in [direction], or docks it
     * along that outer edge when there is no neighbour.
     */
    fun movePane(paneId: PaneId, direction: DockEdge) {
        if (!canMove(paneId, direction)) return
        val group = state.root.tabsOf(paneId) ?: return
        val target = neighbour(group, direction)
        if (target != null) {
            manager.apply(DockCommand.MovePane(paneId, target.active, DockDrop.CENTER))
        } else {
            manager.apply(DockCommand.DockToEdge(paneId, direction))
        }
        focusPane(paneId)
    }

    /** True if [paneId] shares its group with another pane, so it can be split off. */
    fun canSplit(paneId: PaneId): Boolean = (state.root.tabsOf(paneId)?.paneIds?.size ?: 0) > 1

    /** Splits [paneId] out of its group into a new group on [drop]'s side of it. */
    fun splitPane(paneId: PaneId, drop: DockDrop) {
        if (!canSplit(paneId) || drop == DockDrop.CENTER) return
        val other = state.root.tabsOf(paneId)!!.paneIds.first { it != paneId }
        manager.apply(DockCommand.MovePane(paneId, other, drop))
        focusPane(paneId)
    }

    /** Cycles focus through docked groups (their active tabs) and then floating panes. */
    fun focusNextPane(backwards: Boolean = false) {
        val order = state.root.tabGroups().map { it.active } + state.floating.map { it.paneId }
        if (order.isEmpty()) return
        val current = order.indexOf(currentPane())
        val next = when {
            current < 0 -> if (backwards) order.lastIndex else 0
            backwards -> (current - 1).mod(order.size)
            else -> (current + 1).mod(order.size)
        }
        focusPane(order[next])
    }

    /** Selects the next/previous tab within the focused pane's group. */
    fun selectAdjacentTab(backwards: Boolean = false) {
        val group = currentPane()?.let(state.root::tabsOf) ?: return
        val index = group.paneIds.indexOf(group.active)
        focusPane(group.paneIds[(index + if (backwards) -1 else 1).mod(group.paneIds.size)])
    }

    fun resetLayout() {
        val focused = currentPane()
        manager.apply(DockCommand.ReplaceLayout(defaultLayout()))
        focused?.takeIf { it in state.root.paneIds() }?.let(::focusPane)
    }

    fun resizeSplit(path: List<Int>, ratio: Float) {
        manager.apply(DockCommand.ResizeSplit(path, ratio))
    }

    fun onFloatingWindowMoved(paneId: PaneId, bounds: Rectangle) {
        val current = state.floating.firstOrNull { it.paneId == paneId } ?: return
        if (current.bounds != bounds && bounds.width > 0 && bounds.height > 0) {
            manager.apply(DockCommand.SetFloatingBounds(paneId, bounds))
        }
    }

    /** Opens the dock context menu for [paneId] at root-relative ([x], [y]) inside [overlay]. */
    fun showContextMenu(paneId: PaneId, overlay: OverlayHostWidget, x: Int, y: Int): DockContextMenuWidget =
        DockContextMenuWidget.show(this, paneId, overlay, x, y)

    /** Opens the dock context menu for the focused pane, anchored to its header. */
    fun showContextMenuForFocusedPane(): Boolean {
        val paneId = currentPane() ?: return false
        val paneWidget = paneWidgetFor(paneId) ?: return false
        val overlay = paneWidget.findOverlayHost() ?: return false
        showContextMenu(paneId, overlay, paneWidget.bounds.x + 8, paneWidget.bounds.y + DockPaneWidget.HEADER_HEIGHT)
        return true
    }

    /**
     * The pane the user is working in: the one holding the keyboard focus owner, falling back to
     * the layout's focused pane.
     */
    fun currentPane(): PaneId? {
        val focused = installedRoot?.focusManager?.focused
        if (focused != null) {
            registry.all.firstOrNull { focused.isInside(it.content) }?.let { return it.id }
        }
        return state.focusedPane
    }

    // ---------------------------------------------------------------- keyboard

    /**
     * Attaches this host to the main window's [root]: installs the docking key bindings and keeps
     * floating windows on the same theme as the main window.
     */
    fun install(root: RootPane) {
        installedRoot = root
        bindKeys(root.keymap, ::currentPane)
        unsubscribeTheme?.invoke()
        unsubscribeTheme = root.themeProvider.events.subscribe { event ->
            if (event is ThemeChanged) floatingWindows.values.forEach { it.applyTheme(event.theme) }
        }
    }

    /**
     * Binds the docking shortcuts in [keymap], acting on [pane] (the focused pane):
     *
     * - F6 / Shift+F6: next/previous pane
     * - Alt+Shift+Arrow: move the pane to the neighbouring group (or that outer edge)
     * - Ctrl+Alt+Shift+Right/Down: split the pane off to the right/bottom
     * - Alt+Shift+[ / ]: previous/next tab in the group
     * - Alt+Shift+F: float or re-dock the pane
     * - Shift+Escape: hide the pane
     * - Shift+F10 or the context-menu key: open the dock menu
     * - Alt+Shift+R: reset to the default layout
     */
    fun bindKeys(keymap: Keymap, pane: () -> PaneId?) {
        val altShift = KeyEvent.ALT_DOWN_MASK or KeyEvent.SHIFT_DOWN_MASK
        val ctrlAltShift = altShift or KeyEvent.CTRL_DOWN_MASK
        fun bind(keyCode: Int, modifiers: Int, action: (PaneId) -> Unit) =
            keymap.bind(KeyStroke(keyCode, modifiers), Command { pane()?.let { action(it); true } ?: false })

        keymap.bind(KeyStroke(KeyEvent.VK_F6, 0), Command { focusNextPane(); true })
        keymap.bind(KeyStroke(KeyEvent.VK_F6, KeyEvent.SHIFT_DOWN_MASK), Command { focusNextPane(backwards = true); true })
        bind(KeyEvent.VK_LEFT, altShift) { movePane(it, DockEdge.LEFT) }
        bind(KeyEvent.VK_RIGHT, altShift) { movePane(it, DockEdge.RIGHT) }
        bind(KeyEvent.VK_UP, altShift) { movePane(it, DockEdge.TOP) }
        bind(KeyEvent.VK_DOWN, altShift) { movePane(it, DockEdge.BOTTOM) }
        bind(KeyEvent.VK_RIGHT, ctrlAltShift) { splitPane(it, DockDrop.RIGHT) }
        bind(KeyEvent.VK_DOWN, ctrlAltShift) { splitPane(it, DockDrop.BOTTOM) }
        keymap.bind(KeyStroke(KeyEvent.VK_OPEN_BRACKET, altShift), Command { selectAdjacentTab(backwards = true); true })
        keymap.bind(KeyStroke(KeyEvent.VK_CLOSE_BRACKET, altShift), Command { selectAdjacentTab(); true })
        bind(KeyEvent.VK_F, altShift) { if (isFloating(it)) dockPane(it) else floatPane(it) }
        bind(KeyEvent.VK_ESCAPE, KeyEvent.SHIFT_DOWN_MASK) { hidePane(it) }
        keymap.bind(KeyStroke(KeyEvent.VK_F10, KeyEvent.SHIFT_DOWN_MASK), Command { openMenuFor(pane()) })
        keymap.bind(KeyStroke(KeyEvent.VK_CONTEXT_MENU, 0), Command { openMenuFor(pane()) })
        keymap.bind(KeyStroke(KeyEvent.VK_R, altShift), Command { resetLayout(); true })
    }

    private fun openMenuFor(paneId: PaneId?): Boolean {
        paneId ?: return false
        floatingWindows[paneId]?.let { return it.showContextMenu() }
        return showContextMenuForFocusedPane()
    }

    // ---------------------------------------------------------------- state -> widgets

    private fun onStateChanged() {
        val nextShape = shapeOf(state)
        if (nextShape == shape) {
            // Same tree shape (only ratios, active tabs or focus changed): reuse the widgets.
            state.root.tabGroups().zip(paneWidgets).forEach { (group, widget) -> widget.group = group }
            floatingWindows.values.forEach { it.refresh() }
            syncFloatingWindows()
        } else {
            shape = nextShape
            rebuild()
        }
        strips.values.forEach { it.refresh() }
        layout()
        requestRepaint()
    }

    private fun rebuild() {
        // Detach every docked content first so contents can move freely between groups and windows.
        paneWidgets.forEach { it.release(); removeChild(it) }
        splitters.values.forEach(::removeChild)
        syncFloatingWindows()

        val groups = state.root.tabGroups()
        paneWidgets = groups.map { DockPaneWidget(this, it) }
        paneWidgets.forEach(::addChild)
        splitters = buildMap { collectSplitters(state.root, emptyList(), this) }
        // Splitters are added last so their widened hit targets win over pane edges.
        splitters.values.forEach(::addChild)

        installedRoot?.focusManager?.let { focusManager ->
            if (focusManager.focused?.let { it.hostPane !== installedRoot } == true) focusManager.clear()
        }
    }

    private fun collectSplitters(node: DockNode, path: List<Int>, output: MutableMap<List<Int>, DockSplitterWidget>) {
        if (node !is DockNode.Split) return
        output[path] = DockSplitterWidget(this, path, node.axis)
        collectSplitters(node.first, path + 0, output)
        collectSplitters(node.second, path + 1, output)
    }

    private fun syncFloatingWindows() {
        val wanted = state.floating.associateBy { it.paneId }
        floatingWindows.keys.filter { it !in wanted || !floatingWindowsEnabled }.forEach { id ->
            floatingWindows.remove(id)?.close()
        }
        if (!floatingWindowsEnabled) return
        for ((id, floating) in wanted) {
            val existing = floatingWindows[id]
            if (existing == null) {
                floatingWindows[id] = floatingWindowFactory.open(this, registry.require(id), Rectangle(floating.bounds)).also {
                    installedRoot?.let { root -> it.applyTheme(root.themeProvider.current) }
                }
            } else if (existing.bounds != floating.bounds) {
                existing.moveTo(Rectangle(floating.bounds))
            }
        }
    }

    /** Closes every floating window, e.g. when the main window is closing. */
    fun disposeFloatingWindows() {
        floatingWindows.values.toList().forEach { it.close() }
        floatingWindows.clear()
    }

    /** Open floating windows by pane, for tests and diagnostics. */
    val openFloatingWindows: Map<PaneId, FloatingPaneWindow> get() = floatingWindows

    // ---------------------------------------------------------------- layout & paint

    override fun layout() {
        val b = bounds
        var area = Rectangle(b)
        strips[DockEdge.LEFT]!!.let { strip ->
            val w = if (strip.isEmpty) 0 else ToolWindowStripWidget.THICKNESS
            strip.visible = w > 0
            strip.setBounds(area.x, area.y, w, area.height)
            area = Rectangle(area.x + w, area.y, area.width - w, area.height)
        }
        strips[DockEdge.RIGHT]!!.let { strip ->
            val w = if (strip.isEmpty) 0 else ToolWindowStripWidget.THICKNESS
            strip.visible = w > 0
            strip.setBounds(area.x + area.width - w, area.y, w, area.height)
            area = Rectangle(area.x, area.y, area.width - w, area.height)
        }
        strips[DockEdge.BOTTOM]!!.let { strip ->
            val h = if (strip.isEmpty) 0 else ToolWindowStripWidget.THICKNESS
            strip.visible = h > 0
            strip.setBounds(area.x, area.y + area.height - h, area.width, h)
            area = Rectangle(area.x, area.y, area.width, area.height - h)
        }
        val laidOut = mutableListOf<Pair<DockNode.Tabs, Rectangle>>()
        layoutNode(state.root, Rectangle(area.x, area.y, area.width.coerceAtLeast(0), area.height.coerceAtLeast(0)), emptyList(), laidOut)
        groupBounds = laidOut
        laidOut.zip(paneWidgets).forEach { (entry, widget) ->
            val r = entry.second
            widget.setBounds(r.x, r.y, r.width.coerceAtLeast(1), r.height.coerceAtLeast(1))
        }
    }

    private fun layoutNode(node: DockNode, area: Rectangle, path: List<Int>, output: MutableList<Pair<DockNode.Tabs, Rectangle>>) {
        when (node) {
            DockNode.Empty -> Unit
            is DockNode.Tabs -> output += node to area
            is DockNode.Split -> {
                val horizontal = node.axis == Axis.HORIZONTAL
                val total = if (horizontal) area.width else area.height
                val available = (total - GAP).coerceAtLeast(0)
                val firstSize = splitPosition(available, node.ratio)
                val (first, gap, second) = if (horizontal) {
                    Triple(
                        Rectangle(area.x, area.y, firstSize, area.height),
                        Rectangle(area.x + firstSize, area.y, GAP, area.height),
                        Rectangle(area.x + firstSize + GAP, area.y, available - firstSize, area.height),
                    )
                } else {
                    Triple(
                        Rectangle(area.x, area.y, area.width, firstSize),
                        Rectangle(area.x, area.y + firstSize, area.width, GAP),
                        Rectangle(area.x, area.y + firstSize + GAP, area.width, available - firstSize),
                    )
                }
                splitters[path]?.place(gap, area)
                layoutNode(node.first, first, path + 0, output)
                layoutNode(node.second, second, path + 1, output)
            }
        }
    }

    override fun onPaint(g: Graphics2D, localRegion: Rectangle) {
        g.color = theme.background
        g.fillRect(0, 0, bounds.width, bounds.height)
    }

    // ---------------------------------------------------------------- helpers

    internal fun paneWidgetFor(paneId: PaneId): DockPaneWidget? =
        paneWidgets.firstOrNull { paneId in it.group.paneIds }

    internal fun boundsOf(paneId: PaneId): Rectangle? =
        groupBounds.firstOrNull { paneId in it.first.paneIds }?.second

    private fun neighbour(group: DockNode.Tabs, direction: DockEdge): DockNode.Tabs? {
        val from = groupBounds.firstOrNull { it.first == group }?.second ?: return null
        return groupBounds
            .filter { it.first != group }
            .mapNotNull { (other, r) ->
                val (distance, overlap) = when (direction) {
                    DockEdge.LEFT -> (from.x - (r.x + r.width)) to overlap(from.y, from.height, r.y, r.height)
                    DockEdge.RIGHT -> (r.x - (from.x + from.width)) to overlap(from.y, from.height, r.y, r.height)
                    DockEdge.TOP -> (from.y - (r.y + r.height)) to overlap(from.x, from.width, r.x, r.width)
                    DockEdge.BOTTOM -> (r.y - (from.y + from.height)) to overlap(from.x, from.width, r.x, r.width)
                }
                if (distance < -GAP || overlap <= 0) null else Triple(other, distance, overlap)
            }
            .minWithOrNull(compareBy<Triple<DockNode.Tabs, Int, Int>> { it.second }.thenByDescending { it.third })
            ?.first
    }

    private fun defaultFloatingBounds(paneId: PaneId): Rectangle {
        val local = boundsOf(paneId) ?: Rectangle(bounds.x + 80, bounds.y + 80, 0, 0)
        val width = local.width.coerceAtLeast(MIN_FLOATING_WIDTH)
        val height = local.height.coerceAtLeast(MIN_FLOATING_HEIGHT)
        val origin = runCatching { installedRoot?.locationOnScreen }.getOrNull()
        return if (origin != null) {
            Rectangle(origin.x + local.x + FLOAT_OFFSET, origin.y + local.y + FLOAT_OFFSET, width, height)
        } else {
            Rectangle(local.x + FLOAT_OFFSET, local.y + FLOAT_OFFSET, width, height)
        }
    }

    companion object {
        /** Gap between docked groups; the splitter's hit target is wider than this. */
        const val GAP = 4
        const val MIN_PANE_SIZE = 80
        const val MIN_FLOATING_WIDTH = 320
        const val MIN_FLOATING_HEIGHT = 240
        private const val FLOAT_OFFSET = 24

        /** Pixel size of a split's first child, keeping both children at least [MIN_PANE_SIZE] when possible. */
        fun splitPosition(available: Int, ratio: Float): Int {
            val raw = (available * ratio).toInt()
            if (available < MIN_PANE_SIZE * 2) return raw.coerceIn(0, available)
            return raw.coerceIn(MIN_PANE_SIZE, available - MIN_PANE_SIZE)
        }

        /** Tree shape without ratios, active tabs or focus, so those changes skip widget rebuilds. */
        internal fun shapeOf(state: DockLayoutState): String = buildString {
            fun node(n: DockNode) {
                when (n) {
                    DockNode.Empty -> append('e')
                    is DockNode.Tabs -> append(n.paneIds.joinToString(",", "[", "]"))
                    is DockNode.Split -> { append(n.axis.name[0]).append('('); node(n.first); append('|'); node(n.second); append(')') }
                }
            }
            node(state.root)
            append(state.hidden.toSortedMap().entries.joinToString(";", "h{", "}") { "${it.key}=${it.value}" })
            append(state.floating.joinToString(",", "f{", "}") { it.paneId.value })
        }

        private fun overlap(aStart: Int, aSize: Int, bStart: Int, bSize: Int): Int =
            minOf(aStart + aSize, bStart + bSize) - maxOf(aStart, bStart)
    }
}

internal fun Widget.isInside(ancestor: Widget): Boolean {
    var node: Widget? = this
    while (node != null) {
        if (node === ancestor) return true
        node = node.parent
    }
    return false
}

internal fun Widget.findOverlayHost(): OverlayHostWidget? {
    var node = parent
    while (node != null) {
        if (node is OverlayHostWidget) return node
        node = node.parent
    }
    return null
}

internal fun firstFocusable(widget: Widget): Widget? {
    if (!widget.visible || !widget.enabled) return null
    if (widget.focusable) return widget
    return widget.children.firstNotNullOfOrNull(::firstFocusable)
}

/** Detaches [widget] from whatever parent currently holds it. */
internal fun detach(widget: Widget) {
    widget.parent?.removeChild(widget)
}
