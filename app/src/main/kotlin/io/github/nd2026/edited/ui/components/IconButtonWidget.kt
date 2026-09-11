package io.github.nd2026.edited.ui.components

import io.github.nd2026.edited.event.InputEvent
import io.github.nd2026.edited.ui.Semantics
import io.github.nd2026.edited.ui.Widget
import io.github.nd2026.edited.ui.layout.Constraints
import io.github.nd2026.edited.ui.layout.IntSize
import java.awt.BasicStroke
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.event.KeyEvent

fun interface IconPainter {
    fun paint(g: Graphics2D, x: Int, y: Int, size: Int)
}

object MaterialIcons {
    val Close = IconPainter { g, x, y, size ->
        val inset = size / 4
        g.stroke = BasicStroke((size / 10f).coerceAtLeast(1f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.drawLine(x + inset, y + inset, x + size - inset, y + size - inset)
        g.drawLine(x + size - inset, y + inset, x + inset, y + size - inset)
    }
}

class IconButtonWidget(
    val accessibleLabel: String,
    var icon: IconPainter,
    var onClick: () -> Unit = {},
    var diameter: Int = 40,
) : Widget() {
    init {
        focusable = true
        semantics = Semantics(
            role = Semantics.Role.BUTTON,
            name = accessibleLabel,
            actions = setOf(Semantics.Action.ACTIVATE, Semantics.Action.FOCUS),
        )
    }

    override fun onMeasure(constraints: Constraints): IntSize =
        constraints.constrain(IntSize(diameter, diameter))

    override fun onPaint(g: Graphics2D, localRegion: Rectangle) {
        val stateAlpha = when {
            !enabled -> 0
            pressed -> 31
            focused -> 31
            hovered -> 20
            else -> 0
        }
        if (stateAlpha > 0) {
            g.color = theme.onSurface.withAlpha(stateAlpha)
            g.fillOval(0, 0, bounds.width, bounds.height)
        }
        g.color = if (enabled) theme.onSurface else theme.onSurface.withAlpha(97)
        val iconSize = minOf(24, bounds.width, bounds.height)
        icon.paint(g, (bounds.width - iconSize) / 2, (bounds.height - iconSize) / 2, iconSize)
    }

    override fun onMouseEvent(event: InputEvent.MouseInput): Boolean = when (event) {
        is InputEvent.MousePressed -> enabled
        is InputEvent.MouseReleased -> {
            if (pressed && bounds.contains(event.x, event.y)) onClick()
            true
        }
        is InputEvent.MouseCancelled -> true
        else -> false
    }

    override fun onKeyEvent(event: InputEvent.KeyInput): Boolean {
        if (event is InputEvent.KeyPressed &&
            (event.keyCode == KeyEvent.VK_SPACE || event.keyCode == KeyEvent.VK_ENTER)
        ) {
            onClick()
            return true
        }
        return false
    }
}
