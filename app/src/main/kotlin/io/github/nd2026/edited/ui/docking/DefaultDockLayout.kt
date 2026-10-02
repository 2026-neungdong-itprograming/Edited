package io.github.nd2026.edited.ui.docking

/**
 * Texted's default layout: Project/Structure on the left, the editor in the centre,
 * Characters/World/Graph on the right and Problems/Search/Git History along the bottom.
 */
object DefaultDockLayout {
    val PROJECT = PaneId("project")
    val STRUCTURE = PaneId("structure")
    val EDITOR = PaneId("editor")
    val CHARACTERS = PaneId("characters")
    val WORLD = PaneId("world")
    val GRAPH = PaneId("graph")
    val PROBLEMS = PaneId("problems")
    val SEARCH = PaneId("search")
    val GIT_HISTORY = PaneId("git-history")

    val LEFT = listOf(PROJECT, STRUCTURE)
    val RIGHT = listOf(CHARACTERS, WORLD, GRAPH)
    val BOTTOM = listOf(PROBLEMS, SEARCH, GIT_HISTORY)

    /** Edge each default pane belongs to; [EDITOR] is central and has none. */
    val edges: Map<PaneId, DockEdge> =
        LEFT.associateWith { DockEdge.LEFT } + RIGHT.associateWith { DockEdge.RIGHT } + BOTTOM.associateWith { DockEdge.BOTTOM }

    fun state(): DockLayoutState = DockLayoutState(
        root = DockNode.Split(
            axis = Axis.VERTICAL,
            ratio = 0.74f,
            first = DockNode.Split(
                axis = Axis.HORIZONTAL,
                ratio = 0.2f,
                first = DockNode.Tabs(LEFT),
                second = DockNode.Split(
                    axis = Axis.HORIZONTAL,
                    ratio = 0.76f,
                    first = DockNode.Tabs(listOf(EDITOR)),
                    second = DockNode.Tabs(RIGHT),
                ),
            ),
            second = DockNode.Tabs(BOTTOM),
        ),
        focusedPane = EDITOR,
    )
}
