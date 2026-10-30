package io.github.nd2026.edited.ui.components

import io.github.nd2026.edited.event.InputEvent
import io.github.nd2026.edited.ui.OverlayHostWidget
import io.github.nd2026.edited.ui.Semantics
import io.github.nd2026.edited.ui.Widget
import java.awt.BasicStroke
import java.awt.Font
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.KeyEvent
import java.awt.font.FontRenderContext

data class ContextMenuItem(
    val label: String,
    val shortcut: String = "",
    val enabled: Boolean = true,
    val action: () -> Unit,
) {
    /** Rows of the menu that cascades beside this item (opened by hover, click, → or Enter); empty for a command. */
    var submenu: List<ContextMenuItem> = emptyList()
        private set

    companion object {
        /** An item that opens [items] beside the menu instead of running an action ("새로 만들기 ▸"). */
        fun submenu(label: String, items: List<ContextMenuItem>, enabled: Boolean = items.any { it.enabled }): ContextMenuItem =
            ContextMenuItem(label, "", enabled) {}.also { it.submenu = items }
    }
}

/**
 * Toolkit-painted replacement for JPopupMenu. Bounds are root-relative. When focused it is also
 * keyboard operable: Up/Down move the highlight, Enter/Space activate and Escape dismisses.
 *
 * An item with a [ContextMenuItem.submenu] opens another [ContextMenuWidget] beside its row, shown
 * as its own overlay on the nearest [OverlayHostWidget]. Hovering the row opens it, → or Enter
 * opens and focuses it, and ← or Escape inside it closes just that level. Running any command in
 * a submenu dismisses the whole chain through the top menu's `onDismiss`.
 */
open class ContextMenuWidget(
    protected val items: List<ContextMenuItem>,
    private val onDismiss: () -> Unit,
) : Widget() {
    private val rowHeight = ROW_HEIGHT
    private val verticalPadding = 6
    private var pressedIndex = -1

    /** Keyboard/hover highlight, independent of a pointer press. */
    var highlightedIndex = -1
        private set

    /** The open cascading menu; [submenuIndex] is the row it belongs to. */
    var openSubmenu: ContextMenuWidget? = null
        private set
    private var submenuIndex = -1

    /** The menu this one cascades from; null for a top-level menu. */
    private var parentMenu: ContextMenuWidget? = null

    init {
        focusable = true
        semantics = Semantics(
            role = Semantics.Role.MENU,
            actions = setOf(Semantics.Action.DISMISS),
        )
    }

    override fun onPaint(g: Graphics2D, localRegion: Rectangle) {
        g.color = theme.surface
        g.fillRoundRect(0, 0, bounds.width, bounds.height, 12, 12)
        g.color = theme.outline
        g.drawRoundRect(0, 0, bounds.width - 1, bounds.height - 1, 12, 12)
        g.font = theme.labelFont

        items.forEachIndexed { index, item ->
            val y = verticalPadding + index * rowHeight
            if ((index == pressedIndex || index == highlightedIndex || index == submenuIndex) && item.enabled) {
                g.color = theme.primary.withAlpha(24)
                g.fillRoundRect(4, y, bounds.width - 8, rowHeight, 8, 8)
            }
            g.color = if (item.enabled) theme.onSurface else theme.onSurface.withAlpha(80)
            g.drawString(item.label, 16, y + 22)
            if (item.submenu.isNotEmpty()) {
                // Chevron pointing at the cascading menu.
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g.stroke = BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                g.color = if (item.enabled) theme.outline else theme.outline.withAlpha(80)
                val cx = bounds.width - 20
                val cy = y + rowHeight / 2
                g.drawLine(cx - 2, cy - 4, cx + 2, cy)
                g.drawLine(cx + 2, cy, cx - 2, cy + 4)
            } else if (item.shortcut.isNotEmpty()) {
                val shortcutWidth = g.fontMetrics.stringWidth(item.shortcut)
                g.color = if (item.enabled) theme.outline else theme.outline.withAlpha(80)
                g.drawString(item.shortcut, bounds.width - shortcutWidth - 16, y + 22)
            }
        }
    }

    override fun onMouseEvent(event: InputEvent.MouseInput): Boolean = when (event) {
        is InputEvent.MousePressed -> {
            pressedIndex = itemAt(event.y)
            requestRepaint()
            true
        }
        is InputEvent.MouseReleased -> {
            val releasedIndex = itemAt(event.y)
            val item = items.getOrNull(pressedIndex)?.takeIf { releasedIndex == pressedIndex }
            pressedIndex = -1
            if (item != null && item.submenu.isNotEmpty()) {
                showSubmenu(releasedIndex, focus = true)
                requestRepaint()
            } else {
                if (item?.enabled == true) item.action()
                onDismiss()
            }
            true
        }
        is InputEvent.MouseCancelled -> {
            pressedIndex = -1
            onDismiss()
            true
        }
        is InputEvent.MouseMoved -> {
            val index = itemAt(event.y)
            if (index != highlightedIndex) {
                highlightedIndex = index
                requestRepaint()
                // Hovering a row opens its submenu; any other row closes the open one.
                if (index >= 0) showSubmenu(index, focus = false)
            }
            true
        }
        else -> false
    }

    override fun onKeyEvent(event: InputEvent.KeyInput): Boolean {
        if (event !is InputEvent.KeyPressed) return event is InputEvent.KeyTyped
        when (event.keyCode) {
            KeyEvent.VK_DOWN -> moveHighlight(1)
            KeyEvent.VK_UP -> moveHighlight(-1)
            KeyEvent.VK_HOME -> { highlightedIndex = -1; moveHighlight(1) }
            KeyEvent.VK_END -> { highlightedIndex = items.size; moveHighlight(-1) }
            KeyEvent.VK_ENTER, KeyEvent.VK_SPACE -> {
                val item = items.getOrNull(highlightedIndex)
                if (item != null && item.submenu.isNotEmpty()) {
                    showSubmenu(highlightedIndex, focus = true)
                } else {
                    onDismiss()
                    if (item?.enabled == true) item.action()
                }
            }
            // → opens a submenu; otherwise it bubbles up (the menu bar then moves to the next menu).
            KeyEvent.VK_RIGHT -> {
                if (items.getOrNull(highlightedIndex)?.submenu.isNullOrEmpty()) return false
                showSubmenu(highlightedIndex, focus = true)
            }
            KeyEvent.VK_LEFT -> parentMenu?.closeSubmenu() ?: return false
            KeyEvent.VK_ESCAPE -> parentMenu?.closeSubmenu() ?: onDismiss()
            else -> return false
        }
        return true
    }

    // Focus owner before the menu opened, handed back when it closes.
    private var returnFocus: Widget? = null

    override fun onAttach() {
        returnFocus = hostPane?.focusManager?.focused
    }

    override fun onDetach() {
        closeSubmenu()
        val focusManager = hostPane?.focusManager ?: return
        if (focusManager.focused !== this) return
        focusManager.clear()
        returnFocus?.takeIf { it.hostPane === hostPane }?.let(focusManager::requestFocus)
    }

    /** Moves the highlight by [step], skipping disabled items and wrapping around. */
    fun moveHighlight(step: Int) {
        if (items.none { it.enabled }) return
        var index = highlightedIndex
        repeat(items.size) {
            index = (index + step).mod(items.size)
            if (items[index].enabled) {
                highlightedIndex = index
                requestRepaint()
                return
            }
        }
    }

    /**
     * Opens the submenu of row [index] beside it (left of the menu if there is no room on the right),
     * or closes the open one when that row has none. With [focus] the submenu takes the keyboard.
     */
    private fun showSubmenu(index: Int, focus: Boolean) {
        if (index == submenuIndex && openSubmenu != null) {
            if (focus) openSubmenu?.takeFocus()
            return
        }
        closeSubmenu()
        val item = items.getOrNull(index)
        if (item == null || item.submenu.isEmpty() || !item.enabled) return
        val overlay = overlayHost() ?: return
        val menu = ContextMenuWidget(item.submenu) { closeSubmenu(); onDismiss() }
        menu.parentMenu = this
        val width = preferredWidth(item.submenu, theme.labelFont, minWidth = 160)
        val height = preferredHeight(item.submenu.size)
        val ob = overlay.bounds
        val right = bounds.x + bounds.width - 4
        val x = if (right + width <= ob.x + ob.width - 4) right else (bounds.x - width + 4).coerceAtLeast(ob.x + 4)
        val y = (bounds.y + index * rowHeight).coerceIn(ob.y + 4, (ob.y + ob.height - height - 4).coerceAtLeast(ob.y + 4))
        menu.setBounds(x, y, width, height)
        openSubmenu = menu
        submenuIndex = index
        overlay.showOverlay(menu)
        if (focus) menu.takeFocus()
        requestRepaint()
    }

    /** Closes the open submenu (and any below it); focus returns to this menu if the submenu had it. */
    fun closeSubmenu() {
        val menu = openSubmenu ?: return
        openSubmenu = null
        submenuIndex = -1
        (menu.parent as? OverlayHostWidget)?.removeOverlay(menu)
        requestRepaint()
    }

    private fun takeFocus() {
        hostPane?.focusManager?.requestFocus(this)
        if (highlightedIndex < 0) moveHighlight(1)
    }

    private fun overlayHost(): OverlayHostWidget? {
        var node = parent
        while (node != null) {
            if (node is OverlayHostWidget) return node
            node = node.parent
        }
        return null
    }

    private fun itemAt(rootY: Int): Int {
        val localY = rootY - bounds.y - verticalPadding
        if (localY < 0) return -1
        return (localY / rowHeight).takeIf { it in items.indices } ?: -1
    }

    companion object {
        const val ROW_HEIGHT = 34
        fun preferredHeight(itemCount: Int): Int = 12 + itemCount * ROW_HEIGHT

        /** Width that fits the widest label plus its shortcut (or submenu chevron). */
        fun preferredWidth(items: List<ContextMenuItem>, font: Font, minWidth: Int = 200): Int {
            val widest = items.maxOfOrNull {
                font.getStringBounds(it.label, FRC).width + if (it.submenu.isNotEmpty()) 24.0 else font.getStringBounds(it.shortcut, FRC).width
            } ?: 0.0
            return (widest.toInt() + 72).coerceAtLeast(minWidth)
        }

        private val FRC = FontRenderContext(null, true, true)
    }
}
