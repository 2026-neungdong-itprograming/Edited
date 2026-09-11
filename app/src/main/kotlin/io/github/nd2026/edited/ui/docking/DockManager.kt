package io.github.nd2026.edited.ui.docking

/** Applies docking commands to a UI-independent tree and enforces its invariants. */
class DockManager(initialState: DockLayoutState = DockLayoutState()) {
    var state: DockLayoutState = initialState
        private set

    init {
        validate(state)
    }

    fun apply(command: DockCommand): DockLayoutState {
        state = when (command) {
            is DockCommand.MovePane -> move(command)
            is DockCommand.HidePane -> hide(command)
            is DockCommand.RestorePane -> restore(command)
            is DockCommand.FloatPane -> float(command)
            is DockCommand.ClosePane -> removeEverywhere(state, command.paneId)
                .copy(focusedPane = state.focusedPane.takeUnless { it == command.paneId })
            is DockCommand.ResizeSplit -> state.copy(
                root = resize(state.root, command.path, command.ratio.coerceIn(MIN_RATIO, MAX_RATIO)),
            )
            is DockCommand.FocusPane -> {
                require(command.paneId == null || command.paneId in state.allPaneIds()) { "unknown pane: ${command.paneId}" }
                state.copy(focusedPane = command.paneId)
            }
        }
        validate(state)
        return state
    }

    private fun move(command: DockCommand.MovePane): DockLayoutState {
        require(command.paneId in state.allPaneIds()) { "unknown pane: ${command.paneId}" }
        require(command.targetPaneId in state.root.paneIds()) { "dock target is not in the main layout" }
        if (command.paneId == command.targetPaneId) return state

        val removed = removeEverywhere(state, command.paneId)
        return removed.copy(
            root = insert(
                removed.root,
                command.paneId,
                command.targetPaneId,
                command.drop,
                command.tabIndex,
            ),
            focusedPane = command.paneId,
        )
    }

    private fun hide(command: DockCommand.HidePane): DockLayoutState {
        require(command.paneId in state.allPaneIds()) { "unknown pane: ${command.paneId}" }
        val removed = removeEverywhere(state, command.paneId)
        val panes = removed.hidden[command.edge].orEmpty() + command.paneId
        return removed.copy(
            hidden = removed.hidden + (command.edge to panes),
            focusedPane = removed.focusedPane.takeUnless { it == command.paneId },
        )
    }

    private fun restore(command: DockCommand.RestorePane): DockLayoutState {
        require(command.paneId in state.hidden.values.flatten() || state.floating.any { it.paneId == command.paneId }) {
            "pane is neither hidden nor floating: ${command.paneId}"
        }
        val removed = removeEverywhere(state, command.paneId)
        val root = if (removed.root == DockNode.Empty || command.targetPaneId == null) {
            if (removed.root == DockNode.Empty) DockNode.Tabs(listOf(command.paneId))
            else appendToFirstTabs(removed.root, command.paneId)
        } else {
            require(command.targetPaneId in removed.root.paneIds()) { "unknown dock target: ${command.targetPaneId}" }
            insert(removed.root, command.paneId, command.targetPaneId, command.drop, null)
        }
        return removed.copy(root = root, focusedPane = command.paneId)
    }

    private fun float(command: DockCommand.FloatPane): DockLayoutState {
        require(command.paneId in state.allPaneIds()) { "unknown pane: ${command.paneId}" }
        require(command.bounds.width > 0 && command.bounds.height > 0) { "floating bounds must be non-empty" }
        val removed = removeEverywhere(state, command.paneId)
        return removed.copy(
            floating = removed.floating + FloatingPaneState(command.paneId, command.bounds),
            focusedPane = command.paneId,
        )
    }

    private fun removeEverywhere(source: DockLayoutState, paneId: PaneId): DockLayoutState {
        val hidden = source.hidden.mapValues { (_, panes) -> panes.filterNot { it == paneId } }
            .filterValues { it.isNotEmpty() }
        return source.copy(
            root = remove(source.root, paneId),
            hidden = hidden,
            floating = source.floating.filterNot { it.paneId == paneId },
        )
    }

    private fun remove(node: DockNode, paneId: PaneId): DockNode = when (node) {
        DockNode.Empty -> node
        is DockNode.Tabs -> {
            val panes = node.paneIds.filterNot { it == paneId }
            when {
                panes.isEmpty() -> DockNode.Empty
                node.active == paneId -> DockNode.Tabs(panes, panes.first())
                else -> DockNode.Tabs(panes, node.active)
            }
        }
        is DockNode.Split -> normalize(node.copy(first = remove(node.first, paneId), second = remove(node.second, paneId)))
    }

    private fun normalize(split: DockNode.Split): DockNode = when {
        split.first == DockNode.Empty -> split.second
        split.second == DockNode.Empty -> split.first
        else -> split
    }

    private fun insert(
        node: DockNode,
        paneId: PaneId,
        target: PaneId,
        drop: DockDrop,
        tabIndex: Int?,
    ): DockNode = when (node) {
        DockNode.Empty -> error("dock target not found: $target")
        is DockNode.Tabs -> if (target !in node.paneIds) node else when (drop) {
            DockDrop.CENTER -> {
                val panes = node.paneIds.toMutableList()
                panes.add((tabIndex ?: panes.size).coerceIn(0, panes.size), paneId)
                DockNode.Tabs(panes, paneId)
            }
            DockDrop.LEFT -> DockNode.Split(Axis.HORIZONTAL, DEFAULT_RATIO, DockNode.Tabs(listOf(paneId)), node)
            DockDrop.RIGHT -> DockNode.Split(Axis.HORIZONTAL, 1f - DEFAULT_RATIO, node, DockNode.Tabs(listOf(paneId)))
            DockDrop.TOP -> DockNode.Split(Axis.VERTICAL, DEFAULT_RATIO, DockNode.Tabs(listOf(paneId)), node)
            DockDrop.BOTTOM -> DockNode.Split(Axis.VERTICAL, 1f - DEFAULT_RATIO, node, DockNode.Tabs(listOf(paneId)))
        }
        is DockNode.Split -> when {
            target in node.first.paneIds() -> node.copy(first = insert(node.first, paneId, target, drop, tabIndex))
            target in node.second.paneIds() -> node.copy(second = insert(node.second, paneId, target, drop, tabIndex))
            else -> error("dock target not found: $target")
        }
    }

    private fun appendToFirstTabs(node: DockNode, paneId: PaneId): DockNode = when (node) {
        DockNode.Empty -> DockNode.Tabs(listOf(paneId))
        is DockNode.Tabs -> DockNode.Tabs(node.paneIds + paneId, paneId)
        is DockNode.Split -> node.copy(first = appendToFirstTabs(node.first, paneId))
    }

    private fun resize(node: DockNode, path: List<Int>, ratio: Float): DockNode {
        require(node is DockNode.Split) { "split path does not identify a split" }
        if (path.isEmpty()) return node.copy(ratio = ratio)
        return when (path.first()) {
            0 -> node.copy(first = resize(node.first, path.drop(1), ratio))
            1 -> node.copy(second = resize(node.second, path.drop(1), ratio))
            else -> throw IllegalArgumentException("split path entries must be 0 or 1")
        }
    }

    private fun validate(state: DockLayoutState) {
        require(state.schemaVersion > 0) { "schema version must be positive" }
        val panes = state.allPaneIds()
        require(panes.distinct().size == panes.size) { "each pane must occur exactly once" }
        require(state.focusedPane == null || state.focusedPane in panes) { "focused pane must exist" }
    }

    companion object {
        private const val DEFAULT_RATIO = 0.3f
        private const val MIN_RATIO = 0.05f
        private const val MAX_RATIO = 0.95f
    }
}
