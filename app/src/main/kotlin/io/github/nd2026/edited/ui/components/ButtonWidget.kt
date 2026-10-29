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

    private val feedback = PressFeedback { requestRepaint() }

    /** Focus that came from a click: like Windows, only keyboard focus shows the focus ring. */
    private var pointerFocus = false

    override fun onPaint(g: Graphics2D, localRegion: Rectangle) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        if (!focused) pointerFocus = false
        feedback.update(enabled && hovered, enabled && pressed)
        val arc = PressFeedback.CORNER_ARC
        val filled = style == Style.FILLED
        if (filled) {
            // Accent button: 90 % opacity on hover, 80 % while pressed (AccentFillColorSecondary/Tertiary).
            val opacity = 1f - .1f * feedback.hoverAmount - .1f * feedback.pressAmount
            g.color = if (enabled) theme.primary.withAlpha((255 * opacity).toInt()) else theme.onSurface.withAlpha(31)
            g.fillRoundRect(0, 0, bounds.width, bounds.height, arc, arc)
        } else if (enabled) {
            feedback.subtleFill(theme)?.let { g.color = it; g.fillRoundRect(0, 0, bounds.width, bounds.height, arc, arc) }
        }
        if (focused && enabled && !pointerFocus) {
            g.color = theme.onSurface
            g.stroke = BasicStroke(2f)
            g.drawRoundRect(1, 1, bounds.width - 3, bounds.height - 3, arc, arc)
        }
        g.font = theme.labelFont.deriveFont(LABEL_SIZE)
        g.color = when {
            !enabled -> theme.onSurface.withAlpha(97)
            filled -> feedback.content(theme.onPrimary)
            else -> feedback.content(theme.primary)
        }
        val fm = g.fontMetrics
        g.drawString(label, (bounds.width - fm.stringWidth(label)) / 2, (bounds.height + fm.ascent - fm.descent) / 2)
    }

    override fun onMouseEvent(event: InputEvent.MouseInput): Boolean = when (event) {
        is InputEvent.MousePressed -> { pointerFocus = true; enabled }
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
