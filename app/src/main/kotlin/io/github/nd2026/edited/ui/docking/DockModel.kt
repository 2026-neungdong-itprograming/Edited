package io.github.nd2026.edited.ui.docking

import java.awt.Rectangle

@JvmInline
value class PaneId(val value: String) {
    init {
        require(value.isNotBlank()) { "pane id must not be blank" }
    }

    override fun toString(): String = value
}

enum class Axis { HORIZONTAL, VERTICAL }
enum class DockEdge { LEFT, RIGHT, TOP, BOTTOM }
enum class DockDrop { CENTER, LEFT, RIGHT, TOP, BOTTOM }

sealed interface DockNode {
    data class Split(
        val axis: Axis,
        val ratio: Float,
        val first: DockNode,
        val second: DockNode,
    ) : DockNode {
        init {
            require(ratio.isFinite() && ratio > 0f && ratio < 1f) { "split ratio must be between 0 and 1" }
        }
    }

    data class Tabs(
        val paneIds: List<PaneId>,
        val active: PaneId = paneIds.first(),
    ) : DockNode {
        init {
            require(paneIds.isNotEmpty()) { "a tab group must not be empty" }
            require(paneIds.distinct().size == paneIds.size) { "a tab group must not contain duplicate panes" }
            require(active in paneIds) { "the active pane must belong to its tab group" }
        }
    }

    data object Empty : DockNode
}

data class FloatingPaneState(
    val paneId: PaneId,
    val bounds: Rectangle,
)

data class DockLayoutState(
    val root: DockNode = DockNode.Empty,
    val hidden: Map<DockEdge, List<PaneId>> = emptyMap(),
    val floating: List<FloatingPaneState> = emptyList(),
    val focusedPane: PaneId? = null,
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

sealed interface DockCommand {
    data class MovePane(
        val paneId: PaneId,
        val targetPaneId: PaneId,
        val drop: DockDrop,
        val tabIndex: Int? = null,
    ) : DockCommand

    data class HidePane(val paneId: PaneId, val edge: DockEdge) : DockCommand
    data class RestorePane(
        val paneId: PaneId,
        val targetPaneId: PaneId? = null,
        val drop: DockDrop = DockDrop.CENTER,
    ) : DockCommand

    data class FloatPane(val paneId: PaneId, val bounds: Rectangle) : DockCommand
    data class ClosePane(val paneId: PaneId) : DockCommand
    data class ResizeSplit(val path: List<Int>, val ratio: Float) : DockCommand
    data class FocusPane(val paneId: PaneId?) : DockCommand
}

fun DockNode.paneIds(): List<PaneId> = when (this) {
    DockNode.Empty -> emptyList()
    is DockNode.Tabs -> paneIds
    is DockNode.Split -> first.paneIds() + second.paneIds()
}

fun DockLayoutState.allPaneIds(): List<PaneId> =
    root.paneIds() + hidden.values.flatten() + floating.map { it.paneId }
