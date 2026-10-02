package io.github.nd2026.edited.ui.components

import io.github.nd2026.edited.event.InputEvent
import io.github.nd2026.edited.ui.Semantics
import io.github.nd2026.edited.ui.Widget
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.event.KeyEvent

data class ContextMenuItem(
    val label: String,
    val shortcut: String = "",
    val enabled: Boolean = true,
    val action: () -> Unit,
)

/**
 * Toolkit-painted replacement for JPopupMenu. Bounds are root-relative. When focused it is also
 * keyboard operable: Up/Down move the highlight, Enter/Space activate and Escape dismisses.
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
            if ((index == pressedIndex || index == highlightedIndex) && item.enabled) {
                g.color = theme.primary.withAlpha(24)
                g.fillRoundRect(4, y, bounds.width - 8, rowHeight, 8, 8)
            }
            g.color = if (item.enabled) theme.onSurface else theme.onSurface.withAlpha(80)
            g.drawString(item.label, 16, y + 22)
            if (item.shortcut.isNotEmpty()) {
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
            val item = items.getOrNull(pressedIndex)
            if (releasedIndex == pressedIndex && item?.enabled == true) item.action()
            pressedIndex = -1
            onDismiss()
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
                onDismiss()
                if (item?.enabled == true) item.action()
            }
            KeyEvent.VK_ESCAPE -> onDismiss()
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

    private fun itemAt(rootY: Int): Int {
        val localY = rootY - bounds.y - verticalPadding
        if (localY < 0) return -1
        return (localY / rowHeight).takeIf { it in items.indices } ?: -1
    }

    companion object {
        const val ROW_HEIGHT = 34
        fun preferredHeight(itemCount: Int): Int = 12 + itemCount * ROW_HEIGHT
    }
}
