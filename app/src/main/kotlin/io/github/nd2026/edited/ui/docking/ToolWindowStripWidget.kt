package io.github.nd2026.edited.ui.docking

import io.github.nd2026.edited.event.InputEvent
import io.github.nd2026.edited.ui.Semantics
import io.github.nd2026.edited.ui.Widget
import io.github.nd2026.edited.ui.components.withAlpha
import java.awt.Font
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.event.KeyEvent
import java.awt.font.FontRenderContext

/**
 * Edge strip listing the panes hidden at [edge] (panes hidden at the top edge are shown in the
 * left strip). Clicking an entry, or Enter/Space on the keyboard-highlighted one, restores it.
 */
class ToolWindowStripWidget(
    private val host: DockHostWidget,
    val edge: DockEdge,
) : Widget() {
    var items: List<PaneId> = emptyList()
        private set
    private var highlighted = 0

    val isEmpty: Boolean get() = items.isEmpty()

    init {
        focusable = true
        semantics = Semantics(role = Semantics.Role.TAB_LIST, name = "숨긴 pane (${edge.name.lowercase()})")
    }

    fun refresh() {
        val hidden = host.state.hidden
        items = hidden[edge].orEmpty() + if (edge == DockEdge.LEFT) hidden[DockEdge.TOP].orEmpty() else emptyList()
        highlighted = highlighted.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        focusable = items.isNotEmpty()
        requestRepaint()
    }

    private val vertical get() = edge != DockEdge.BOTTOM

    /** Item rectangles in widget-local coordinates. */
    fun itemRects(): List<Pair<PaneId, Rectangle>> {
        var offset = 6
        return items.map { id ->
            val length = textWidth(theme.labelFont, title(id)) + ITEM_PADDING * 2
            val rect = if (vertical) Rectangle(2, offset, THICKNESS - 4, length) else Rectangle(offset, 2, length, THICKNESS - 4)
            offset += length + 4
            id to rect
        }
    }

    override fun onPaint(g: Graphics2D, localRegion: Rectangle) {
        g.color = theme.surface
        g.fillRect(0, 0, bounds.width, bounds.height)
        g.color = theme.outline.withAlpha(90)
        when (edge) {
            DockEdge.LEFT, DockEdge.TOP -> g.drawLine(bounds.width - 1, 0, bounds.width - 1, bounds.height)
            DockEdge.RIGHT -> g.drawLine(0, 0, 0, bounds.height)
            DockEdge.BOTTOM -> g.drawLine(0, 0, bounds.width, 0)
        }
        g.font = theme.labelFont
        val metrics = g.fontMetrics
        itemRects().forEachIndexed { index, (id, r) ->
            if (focused && index == highlighted) {
                g.color = theme.primary.withAlpha(40)
                g.fillRoundRect(r.x, r.y, r.width, r.height, 8, 8)
            }
            g.color = theme.onSurface
            val text = title(id)
            if (vertical) {
                val rotated = g.create() as Graphics2D
                try {
                    // Read top-to-bottom on the right strip, bottom-to-top on the left, like IDE edge strips.
                    if (edge == DockEdge.RIGHT) {
                        rotated.translate(r.x + (r.width - metrics.height) / 2 + metrics.descent, r.y + ITEM_PADDING)
                        rotated.rotate(Math.PI / 2)
                    } else {
                        rotated.translate(r.x + (r.width + metrics.height) / 2 - metrics.descent, r.y + r.height - ITEM_PADDING)
                        rotated.rotate(-Math.PI / 2)
                    }
                    rotated.drawString(text, 0, 0)
                } finally {
                    rotated.dispose()
                }
            } else {
                g.drawString(text, r.x + ITEM_PADDING, r.y + (r.height + metrics.ascent - metrics.descent) / 2)
            }
        }
    }

    override fun onMouseEvent(event: InputEvent.MouseInput): Boolean {
        if (event !is InputEvent.MousePressed) return false
        val hit = itemRects().firstOrNull { it.second.contains(event.x - bounds.x, event.y - bounds.y) } ?: return false
        host.restorePane(hit.first)
        return true
    }

    override fun onKeyEvent(event: InputEvent.KeyInput): Boolean {
        if (event !is InputEvent.KeyPressed || items.isEmpty()) return false
        val previous = if (vertical) KeyEvent.VK_UP else KeyEvent.VK_LEFT
        val next = if (vertical) KeyEvent.VK_DOWN else KeyEvent.VK_RIGHT
        when (event.keyCode) {
            previous -> highlighted = (highlighted - 1).mod(items.size)
            next -> highlighted = (highlighted + 1).mod(items.size)
            KeyEvent.VK_ENTER, KeyEvent.VK_SPACE -> host.restorePane(items[highlighted])
            else -> return false
        }
        requestRepaint()
        return true
    }

    private fun title(id: PaneId) = host.registry[id]?.title ?: id.value

    companion object {
        const val THICKNESS = 26
        private const val ITEM_PADDING = 10
        private val frc = FontRenderContext(null, true, true)

        private fun textWidth(font: Font, text: String): Int =
            kotlin.math.ceil(font.getStringBounds(text, frc).width).toInt()
    }
}
