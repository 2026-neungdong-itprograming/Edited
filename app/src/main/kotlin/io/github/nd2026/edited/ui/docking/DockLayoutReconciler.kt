package io.github.nd2026.edited.ui.docking

/**
 * Makes a decoded layout consistent with the panes that exist now: unknown pane ids and
 * duplicates are dropped, empty groups collapse, and required panes missing from the saved
 * layout are merged back at their position in [defaults].
 */
object DockLayoutReconciler {
    fun reconcile(saved: DockLayoutState, registry: PaneRegistry, defaults: DockLayoutState): DockLayoutState {
        val seen = mutableSetOf<PaneId>()
        fun keep(id: PaneId) = id in registry && seen.add(id)

        val root = prune(saved.root, ::keep)
        val hidden = saved.hidden.mapValues { (_, panes) -> panes.filter(::keep) }.filterValues { it.isNotEmpty() }
        val floating = saved.floating.filter { it.bounds.width > 0 && it.bounds.height > 0 && keep(it.paneId) }
        val manager = DockManager(
            DockLayoutState(
                root = root,
                hidden = hidden,
                floating = floating,
                focusedPane = saved.focusedPane?.takeIf { it in seen },
            ),
        )

        for (descriptor in registry.all) {
            if (!descriptor.required || descriptor.id in seen) continue
            mergeMissing(manager, descriptor, registry, defaults)
        }
        val state = manager.state
        return if (state.focusedPane == null) state.copy(focusedPane = state.root.paneIds().firstOrNull()) else state
    }

    private fun mergeMissing(manager: DockManager, descriptor: PaneDescriptor, registry: PaneRegistry, defaults: DockLayoutState) {
        val id = descriptor.id
        // Park the pane as hidden so the regular restore commands can place it.
        val parkEdge = descriptor.defaultEdge ?: DockEdge.RIGHT
        manager.apply(DockCommand.ReplaceLayout(manager.state.copy(
            hidden = manager.state.hidden + (parkEdge to manager.state.hidden[parkEdge].orEmpty() + id),
        )))
        val docked = manager.state.root.paneIds()
        val defaultSiblings = defaults.root.tabsOf(id)?.paneIds.orEmpty()
        val sibling = defaultSiblings.firstOrNull { it != id && it in docked }
            ?: docked.firstOrNull { other -> other != id && registry[other]?.defaultEdge == descriptor.defaultEdge }
        val focused = manager.state.focusedPane
        val siblingActive = sibling?.let { manager.state.root.tabsOf(it)?.active }
        when {
            sibling != null -> manager.apply(DockCommand.RestorePane(id, sibling, DockDrop.CENTER))
            docked.isEmpty() -> manager.apply(DockCommand.RestorePane(id))
            descriptor.defaultEdge != null -> manager.apply(DockCommand.DockToEdge(id, descriptor.defaultEdge))
            else -> manager.apply(DockCommand.DockToEdge(id, DockEdge.RIGHT, ratio = 0.7f))
        }
        // Restoring activates and focuses the merged pane; keep the saved active tab and focus instead.
        siblingActive?.let { manager.apply(DockCommand.FocusPane(it)) }
        manager.apply(DockCommand.FocusPane(focused))
    }

    private fun prune(node: DockNode, keep: (PaneId) -> Boolean): DockNode = when (node) {
        DockNode.Empty -> node
        is DockNode.Tabs -> {
            val panes = node.paneIds.filter(keep)
            if (panes.isEmpty()) DockNode.Empty else DockNode.Tabs(panes, node.active.takeIf { it in panes } ?: panes.first())
        }
        is DockNode.Split -> {
            val first = prune(node.first, keep)
            val second = prune(node.second, keep)
            when {
                first == DockNode.Empty -> second
                second == DockNode.Empty -> first
                else -> DockNode.Split(node.axis, clampRatio(node.ratio), first, second)
            }
        }
    }
}
