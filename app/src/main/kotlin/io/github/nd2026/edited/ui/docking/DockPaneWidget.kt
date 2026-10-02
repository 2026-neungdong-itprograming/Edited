package io.github.nd2026.edited.ui.docking

import io.github.nd2026.edited.event.InputEvent
import io.github.nd2026.edited.ui.Container
import io.github.nd2026.edited.ui.Semantics
import io.github.nd2026.edited.ui.Widget
import io.github.nd2026.edited.ui.components.IconButtonWidget
import io.github.nd2026.edited.ui.components.IconPainter
import io.github.nd2026.edited.ui.components.withAlpha
import java.awt.BasicStroke
import java.awt.Font
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.font.FontRenderContext

/**
 * One tab group: a header with the group's tabs and pane actions, above the active pane's content.
 * When [floating] it is the whole content of a [FloatingDockWindow] and offers "dock" instead of "hide".
 */
class DockPaneWidget(
    private val host: DockHostWidget,
    group: DockNode.Tabs,
    val floating: Boolean = false,
) : Container() {
    private var content: Widget? = null

    private val hideButton = IconButtonWidget("숨기기", DockIcons.Minimize, diameter = BUTTON_SIZE)
    private val dockButton = IconButtonWidget("도킹", DockIcons.DockBack, diameter = BUTTON_SIZE)
    private val menuButton = IconButtonWidget("Pane 메뉴", DockIcons.More, diameter = BUTTON_SIZE)

    var group: DockNode.Tabs = group
        set(value) {
            val changed = field != value
            field = value
            if (changed) attachActive()
        }

    init {
        semantics = Semantics(role = Semantics.Role.TAB_LIST, name = "Dock pane")
        addChild(hideButton)
        addChild(dockButton)
        addChild(menuButton)
        hideButton.onClick = { host.hidePane(group.active) }
        dockButton.onClick = { host.dockPane(group.active) }
        menuButton.onClick = { openMenu(bounds.x + bounds.width - BUTTON_SIZE, bounds.y + HEADER_HEIGHT) }
        attachActive()
    }

    private fun attachActive() {
        val next = host.registry.require(group.active).content
        if (next !== content || next.parent !== this) {
            content?.takeIf { it.parent === this }?.let(::removeChild)
            detach(next)
            content = next
            // Content goes first so the header buttons stay on top for hit-testing.
            insertChild(0, next)
        }
        val descriptor = host.registry.require(group.active)
        hideButton.visible = !floating && descriptor.hideable
        dockButton.visible = floating
        layout()
        requestRepaint()
    }

    /** Detaches the content so another group or window can take it. */
    fun release() {
        content?.takeIf { it.parent === this }?.let(::removeChild)
        content = null
    }

    val activePane: PaneId get() = group.active

    override fun layout() {
        val b = bounds
        var right = b.x + b.width - 4
        for (button in listOf(menuButton, dockButton, hideButton)) {
            if (!button.visible) continue
            right -= BUTTON_SIZE
            button.setBounds(right, b.y + (HEADER_HEIGHT - BUTTON_SIZE) / 2, BUTTON_SIZE, BUTTON_SIZE)
        }
        content?.setBounds(b.x + 1, b.y + HEADER_HEIGHT, (b.width - 2).coerceAtLeast(1), (b.height - HEADER_HEIGHT - 1).coerceAtLeast(1))
    }

    /**
     * Tab rectangles in widget-local coordinates, measured without a Graphics context. Tabs that
     * would run under the header buttons are left out; they stay reachable with Alt+Shift+[ / ].
     */
    fun tabRects(): List<Pair<PaneId, Rectangle>> {
        val limit = bounds.width - visibleButtonCount() * BUTTON_SIZE - 8
        return allTabRects().filter { it.second.x + it.second.width <= limit || it.first == group.active }
    }

    private fun visibleButtonCount() = listOf(menuButton, dockButton, hideButton).count { it.visible }

    private fun allTabRects(): List<Pair<PaneId, Rectangle>> {
        val font = theme.labelFont
        var x = 4
        return group.paneIds.map { id ->
            val title = host.registry[id]?.title ?: id.value
            val width = textWidth(font, title) + TAB_PADDING * 2
            (id to Rectangle(x, 0, width, HEADER_HEIGHT)).also { x += width }
        }
    }

    override fun onPaint(g: Graphics2D, localRegion: Rectangle) {
        val focused = host.state.focusedPane in group.paneIds
        g.color = theme.surface
        g.fillRect(0, 0, bounds.width, bounds.height)
        g.color = theme.primary.withAlpha(if (focused) 18 else 8)
        g.fillRect(0, 0, bounds.width, HEADER_HEIGHT)

        g.font = theme.labelFont
        val metrics = g.fontMetrics
        val baseline = (HEADER_HEIGHT + metrics.ascent - metrics.descent) / 2
        for ((id, rect) in tabRects()) {
            val active = id == group.active
            if (active) {
                g.color = if (focused) theme.primary else theme.outline
                g.fillRect(rect.x, HEADER_HEIGHT - 2, rect.width, 2)
            }
            g.color = if (active) theme.onSurface else theme.outline
            g.drawString(host.registry[id]?.title ?: id.value, rect.x + TAB_PADDING, baseline)
        }

        g.color = if (focused) theme.primary.withAlpha(140) else theme.outline.withAlpha(110)
        g.drawRect(0, 0, bounds.width - 1, bounds.height - 1)
        g.color = theme.outline.withAlpha(90)
        g.drawLine(0, HEADER_HEIGHT - 1, bounds.width, HEADER_HEIGHT - 1)
    }

    override fun onMouseEvent(event: InputEvent.MouseInput): Boolean {
        if (event !is InputEvent.MousePressed) return false
        val localX = event.x - bounds.x
        val localY = event.y - bounds.y
        if (localY >= HEADER_HEIGHT) return false
        val tab = tabRects().firstOrNull { it.second.contains(localX, localY) }?.first
        when (event.button) {
            BUTTON_PRIMARY -> host.focusPane(tab ?: group.active)
            BUTTON_MIDDLE -> tab?.let(host::hidePane)
            BUTTON_SECONDARY -> {
                val paneId = tab ?: group.active
                host.focusPane(paneId)
                openMenu(event.x, event.y, paneId)
            }
        }
        return true
    }

    private fun openMenu(x: Int, y: Int, paneId: PaneId = group.active) {
        val overlay = findOverlayHost() ?: return
        host.showContextMenu(paneId, overlay, x, y)
    }

    companion object {
        const val HEADER_HEIGHT = 32
        private const val BUTTON_SIZE = 26
        private const val TAB_PADDING = 12
        private const val BUTTON_PRIMARY = 1
        private const val BUTTON_MIDDLE = 2
        private const val BUTTON_SECONDARY = 3
        private val frc = FontRenderContext(null, true, true)

        private fun textWidth(font: Font, text: String): Int =
            kotlin.math.ceil(font.getStringBounds(text, frc).width).toInt()
    }
}

/** Small vector icons used by docking chrome. */
object DockIcons {
    val Minimize = IconPainter { g, x, y, size ->
        g.stroke = BasicStroke((size / 10f).coerceAtLeast(1f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.drawLine(x + size / 4, y + size * 3 / 5, x + size * 3 / 4, y + size * 3 / 5)
    }

    val More = IconPainter { g, x, y, size ->
        val dot = (size / 8).coerceAtLeast(2)
        val cx = x + size / 2 - dot / 2
        for (i in -1..1) g.fillOval(cx, y + size / 2 - dot / 2 + i * size / 4, dot, dot)
    }

    val DockBack = IconPainter { g, x, y, size ->
        g.stroke = BasicStroke((size / 12f).coerceAtLeast(1f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        val inset = size / 4
        g.drawRect(x + inset, y + inset, size - inset * 2, size - inset * 2)
        g.drawLine(x + inset, y + size / 2, x + size - inset, y + size / 2)
    }
}
