package io.github.nd2026.edited.ui.components

import io.github.nd2026.edited.event.InputEvent
import io.github.nd2026.edited.ui.Semantics
import io.github.nd2026.edited.ui.Widget
import io.github.nd2026.edited.ui.layout.Constraints
import io.github.nd2026.edited.ui.layout.IntSize
import java.awt.BasicStroke
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.Rectangle
import java.awt.event.KeyEvent
import java.awt.font.FontRenderContext
import kotlin.math.ceil

/** Material 3 text/filled button painted with Java2D. */
class ButtonWidget(
    label: String,
    var style: Style = Style.TEXT,
    var onClick: () -> Unit = {},
) : Widget() {
    enum class Style { FILLED, TEXT }

    var label: String = label
        set(value) {
            if (field == value) return
            field = value
            semantics = semantics.copy(name = value)
            requestRepaint()
        }

    init {
        focusable = true
        semantics = Semantics(
            role = Semantics.Role.BUTTON,
            name = label,
            actions = setOf(Semantics.Action.ACTIVATE, Semantics.Action.FOCUS),
        )
    }

    override fun onMeasure(constraints: Constraints): IntSize {
        val textWidth = theme.labelFont.deriveFont(LABEL_SIZE).getStringBounds(label, FRC).width
        return constraints.constrain(IntSize(ceil(textWidth).toInt() + 2 * HORIZONTAL_PADDING, HEIGHT))
    }

    override fun onPaint(g: Graphics2D, localRegion: Rectangle) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        val arc = bounds.height
        val filled = style == Style.FILLED
        val contentColor = when {
            !enabled -> theme.onSurface.withAlpha(97)
            filled -> theme.onPrimary
            else -> theme.primary
        }
        if (filled) {
            g.color = if (enabled) theme.primary else theme.onSurface.withAlpha(31)
            g.fillRoundRect(0, 0, bounds.width, bounds.height, arc, arc)
        }
        val stateAlpha = when {
            !enabled -> 0
            pressed || focused -> 31
            hovered -> 20
            else -> 0
        }
        if (stateAlpha > 0) {
            g.color = (if (filled) theme.onPrimary else theme.primary).withAlpha(stateAlpha)
            g.fillRoundRect(0, 0, bounds.width, bounds.height, arc, arc)
        }
        if (focused && enabled) {
            g.color = theme.primary
            g.stroke = BasicStroke(2f)
            g.drawRoundRect(1, 1, bounds.width - 3, bounds.height - 3, arc, arc)
        }
        g.font = theme.labelFont.deriveFont(LABEL_SIZE)
        g.color = contentColor
        val fm = g.fontMetrics
        g.drawString(label, (bounds.width - fm.stringWidth(label)) / 2, (bounds.height + fm.ascent - fm.descent) / 2)
    }

    override fun onMouseEvent(event: InputEvent.MouseInput): Boolean = when (event) {
        is InputEvent.MousePressed -> enabled
        is InputEvent.MouseReleased -> {
            if (enabled && pressed && bounds.contains(event.x, event.y)) onClick()
            true
        }
        is InputEvent.MouseCancelled -> true
        else -> false
    }

    override fun onKeyEvent(event: InputEvent.KeyInput): Boolean {
        if (enabled && event is InputEvent.KeyPressed &&
            (event.keyCode == KeyEvent.VK_SPACE || event.keyCode == KeyEvent.VK_ENTER)
        ) {
            onClick()
            return true
        }
        return false
    }

    companion object {
        const val HEIGHT = 40
        private const val HORIZONTAL_PADDING = 24
        private const val LABEL_SIZE = 14f
        private val FRC = FontRenderContext(null, true, true)
    }
}
