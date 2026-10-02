package io.github.nd2026.edited.ui.editor

import io.github.nd2026.edited.event.InputEvent
import io.github.nd2026.edited.lexer.DefaultMorphemeAnalyzer
import io.github.nd2026.edited.lexer.MorphemeAnalyzer
import io.github.nd2026.edited.ui.Container
import io.github.nd2026.edited.ui.Widget
import io.github.nd2026.edited.ui.components.TabBarWidget
import io.github.nd2026.edited.ui.components.TabItem
import io.github.nd2026.edited.ui.components.withAlpha
import io.github.nd2026.edited.ui.docking.Axis
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.event.MouseEvent

/** One tab strip plus the editor of its selected document. A leaf of [EditorGroupWidget]'s split tree. */
class EditorLeafWidget internal constructor(
    private val analyzer: MorphemeAnalyzer,
    private val onLastTabClosed: (EditorLeafWidget) -> Unit,
) : Container() {
    private val tabs = TabBarWidget()
    private val documents = linkedMapOf<String, EditorDocument>()
    private val panes = hashMapOf<String, EditorPaneWidget>()
    private var activePane: EditorPaneWidget? = null

    val activeDocument: EditorDocument? get() = activePane?.document
    val openDocuments: List<EditorDocument> get() = documents.values.toList()
    val activeEditor get() = activePane?.editor

    init {
        addChild(tabs)
        tabs.onSelect = ::select
        tabs.onClose = ::close
    }

    fun open(document: EditorDocument) {
        if (document.id !in documents) {
            documents[document.id] = document
            tabs.tabs = documents.values.map { TabItem(it.id, it.title) }
        }
        tabs.selectedId = document.id
        select(document.id)
    }

    fun close(id: String) {
        val removed = documents[id] ?: return
        if (documents.size == 1) {
            onLastTabClosed(this)
            return
        }
        val wasActive = activePane?.document === removed
        documents.remove(id)
        panes.remove(id)?.let { if (it === activePane) removeChild(it) }
        tabs.tabs = documents.values.map { TabItem(it.id, it.title) }
        if (wasActive) {
            activePane = null
            select(tabs.selectedId ?: return)
        }
    }

    private fun select(id: String) {
        val document = documents[id] ?: return
        val next = panes.getOrPut(id) { EditorPaneWidget(document, analyzer) }
        if (next === activePane) return
        activePane?.let(::removeChild)
        activePane = next
        addChild(next)
        layout()
        requestRepaint()
    }

    override fun layout() {
        val b = bounds
        tabs.setBounds(b.x, b.y, b.width, TAB_HEIGHT)
        activePane?.setBounds(b.x, b.y + TAB_HEIGHT, b.width, (b.height - TAB_HEIGHT).coerceAtLeast(1))
    }

    override fun onPaint(g: Graphics2D, localRegion: Rectangle) {
        g.color = theme.surface
        g.fillRect(0, 0, bounds.width, bounds.height)
    }

    /** Drops every cached pane (stops their background work) when the leaf itself is discarded. */
    internal fun dispose() {
        activePane?.let(::removeChild)
        activePane = null
        panes.clear()
    }

    private companion object {
        const val TAB_HEIGHT = 40
    }
}

/**
 * A split editor: a binary tree of [EditorLeafWidget]s separated by draggable splitters, the way
 * IntelliJ splits its editor area. [split] shows the active document in a new pane beside or below
 * the current one; closing a pane's last tab removes the pane and its sibling takes the space.
 *
 * The tree shape and ratios are plain data in [Node]; layout, drag handling and painting are the only
 * widget work, and the leaves own their documents' views.
 */
class EditorGroupWidget(
    private val analyzer: MorphemeAnalyzer = DefaultMorphemeAnalyzer,
) : Container() {

    private sealed interface Node {
        class Leaf(val widget: EditorLeafWidget) : Node
        class Split(val axis: Axis, var ratio: Float, var first: Node, var second: Node) : Node {
            /** Area this split divides, in root coordinates; set by [layout], used to turn a drag into a ratio. */
            var area = Rectangle()
        }
    }

    private var root: Node = newLeaf()
    private var lastActive: EditorLeafWidget = (root as Node.Leaf).widget
    private var draggedSplit: Node.Split? = null

    init {
        addChild(lastActive)
    }

    val leafCount: Int get() = leaves().size

    /** The pane that has keyboard focus, else the one used last. */
    val activeLeaf: EditorLeafWidget
        get() {
            var node: Widget? = hostPane?.focusManager?.focused
            while (node != null) {
                if (node is EditorLeafWidget && leaves().contains(node)) {
                    lastActive = node
                    break
                }
                node = node.parent
            }
            return lastActive
        }

    /** Opens [document] in the active pane (selecting its tab if it is already open there). */
    fun open(document: EditorDocument) = activeLeaf.open(document)

    /** Splits the active pane; the new pane shows the same document. Returns false if nothing is open. */
    fun split(axis: Axis): Boolean {
        val leaf = activeLeaf
        val document = leaf.activeDocument ?: return false
        val fresh = newLeaf()
        addChild(fresh.widget)
        fresh.widget.open(document)
        root = replace(root, leaf) { Node.Split(axis, 0.5f, it, fresh) }
        lastActive = fresh.widget
        layout()
        requestRepaint()
        return true
    }

    /** Closes the active pane if another remains; its sibling absorbs the space. */
    fun closeActivePane(): Boolean = removeLeaf(activeLeaf)

    private fun newLeaf(): Node.Leaf = Node.Leaf(EditorLeafWidget(analyzer, onLastTabClosed = { removeLeaf(it) }))

    private fun removeLeaf(leaf: EditorLeafWidget): Boolean {
        if (leaves().size <= 1) return false
        root = remove(root, leaf) ?: return false
        removeChild(leaf)
        leaf.dispose()
        if (lastActive === leaf) lastActive = leaves().first()
        hostPane?.focusManager?.clear()
        layout()
        requestRepaint()
        return true
    }

    private fun leaves(node: Node = root): List<EditorLeafWidget> = when (node) {
        is Node.Leaf -> listOf(node.widget)
        is Node.Split -> leaves(node.first) + leaves(node.second)
    }

    private fun replace(node: Node, target: EditorLeafWidget, make: (Node.Leaf) -> Node): Node = when (node) {
        is Node.Leaf -> if (node.widget === target) make(node) else node
        is Node.Split -> {
            node.first = replace(node.first, target, make)
            node.second = replace(node.second, target, make)
            node
        }
    }

    /** Removes [target]; a split that loses one side is replaced by the other side. */
    private fun remove(node: Node, target: EditorLeafWidget): Node? = when (node) {
        is Node.Leaf -> if (node.widget === target) null else node
        is Node.Split -> {
            val first = remove(node.first, target)
            val second = remove(node.second, target)
            when {
                first == null -> second
                second == null -> first
                else -> { node.first = first; node.second = second; node }
            }
        }
    }

    override fun layout() {
        layoutNode(root, Rectangle(bounds))
    }

    private fun layoutNode(node: Node, area: Rectangle) {
        when (node) {
            is Node.Leaf -> node.widget.setBounds(area.x, area.y, area.width.coerceAtLeast(1), area.height.coerceAtLeast(1))
            is Node.Split -> {
                node.area = Rectangle(area)
                val total = if (node.axis == Axis.HORIZONTAL) area.width else area.height
                val usable = (total - SPLITTER).coerceAtLeast(2)
                val minSize = MIN_PANE.coerceAtMost(usable / 2)
                val firstSize = (usable * node.ratio).toInt().coerceIn(minSize, usable - minSize)
                if (node.axis == Axis.HORIZONTAL) {
                    layoutNode(node.first, Rectangle(area.x, area.y, firstSize, area.height))
                    layoutNode(node.second, Rectangle(area.x + firstSize + SPLITTER, area.y, usable - firstSize, area.height))
                } else {
                    layoutNode(node.first, Rectangle(area.x, area.y, area.width, firstSize))
                    layoutNode(node.second, Rectangle(area.x, area.y + firstSize + SPLITTER, area.width, usable - firstSize))
                }
            }
        }
    }

    /** The splitter bar of [split], in root coordinates. */
    private fun dividerOf(split: Node.Split): Rectangle {
        val usable = (if (split.axis == Axis.HORIZONTAL) split.area.width else split.area.height) - SPLITTER
        val minSize = MIN_PANE.coerceAtMost(usable / 2)
        val firstSize = (usable * split.ratio).toInt().coerceIn(minSize, (usable - minSize).coerceAtLeast(minSize))
        return if (split.axis == Axis.HORIZONTAL) {
            Rectangle(split.area.x + firstSize, split.area.y, SPLITTER, split.area.height)
        } else {
            Rectangle(split.area.x, split.area.y + firstSize, split.area.width, SPLITTER)
        }
    }

    private fun splits(node: Node = root): List<Node.Split> = when (node) {
        is Node.Leaf -> emptyList()
        is Node.Split -> listOf(node) + splits(node.first) + splits(node.second)
    }

    override fun onPaint(g: Graphics2D, localRegion: Rectangle) {
        g.color = theme.outline.withAlpha(40)
        g.fillRect(0, 0, bounds.width, bounds.height)
        for (split in splits()) {
            val d = dividerOf(split)
            g.color = if (split === draggedSplit) theme.primary.withAlpha(140) else theme.outline.withAlpha(90)
            g.fillRect(d.x - bounds.x, d.y - bounds.y, d.width, d.height)
        }
    }

    override fun onMouseEvent(event: InputEvent.MouseInput): Boolean = when (event) {
        is InputEvent.MousePressed -> {
            // Only the gap between panes reaches this widget; children claim every other press.
            draggedSplit = if (event.button == MouseEvent.BUTTON1) splits().firstOrNull { dividerOf(it).contains(event.x, event.y) } else null
            requestRepaint()
            draggedSplit != null
        }
        is InputEvent.MouseDragged -> draggedSplit?.let { drag(it, event.x, event.y); true } ?: false
        is InputEvent.MouseReleased, is InputEvent.MouseCancelled -> {
            val was = draggedSplit != null
            draggedSplit = null
            requestRepaint()
            was
        }
        else -> false
    }

    private fun drag(split: Node.Split, x: Int, y: Int) {
        val horizontal = split.axis == Axis.HORIZONTAL
        val usable = ((if (horizontal) split.area.width else split.area.height) - SPLITTER).coerceAtLeast(1)
        val position = (if (horizontal) x - split.area.x else y - split.area.y) - SPLITTER / 2
        val minSize = MIN_PANE.coerceAtMost(usable / 2)
        split.ratio = (position.coerceIn(minSize, usable - minSize).toFloat() / usable)
        layout()
        requestRepaint()
    }

    companion object {
        const val SPLITTER = 6
        const val MIN_PANE = 160
    }
}
