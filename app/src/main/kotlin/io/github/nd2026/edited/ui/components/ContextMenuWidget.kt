package io.github.nd2026.edited.ui.components

import io.github.nd2026.edited.event.InputEvent
import io.github.nd2026.edited.ui.Semantics
import io.github.nd2026.edited.ui.Widget
import java.awt.Graphics2D
import java.awt.Rectangle

data class ContextMenuItem(
    val label: String,
    val shortcut: String = "",
    val enabled: Boolean = true,
    val action: () -> Unit,
)

/** Toolkit-painted replacement for JPopupMenu. Bounds are root-relative. */
class ContextMenuWidget(
    private val items: List<ContextMenuItem>,
    private val onDismiss: () -> Unit,
) : Widget() {
    private val rowHeight = 34
    private val verticalPadding = 6
    private var pressedIndex = -1

    init {
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
            if (index == pressedIndex && item.enabled) {
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
        else -> false
    }

    private fun itemAt(rootY: Int): Int {
        val localY = rootY - bounds.y - verticalPadding
        if (localY < 0) return -1
        return (localY / rowHeight).takeIf { it in items.indices } ?: -1
    }

    companion object {
        fun preferredHeight(itemCount: Int): Int = 12 + itemCount * 34
    }
}
