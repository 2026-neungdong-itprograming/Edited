package io.github.nd2026.edited.ui.components

import io.github.nd2026.edited.event.InputEvent
import io.github.nd2026.edited.ui.Container
import io.github.nd2026.edited.ui.OverlayHostWidget
import io.github.nd2026.edited.ui.Semantics
import io.github.nd2026.edited.ui.Widget
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.KeyEvent

/**
 * Material 3 modal dialog: a scrim over the whole [OverlayHostWidget] and a centered card with a
 * [title], a [body] of fixed height, and right-aligned [actions]. Shown through
 * [OverlayHostWidget.showOverlay] as modal, so focus is confined to the card and clicks on the
 * scrim are swallowed. Escape triggers [onCancel].
 */
class DialogWidget(
    private val title: String,
    private val body: Widget,
    private val bodyHeight: Int,
    private val actions: List<ButtonWidget>,
    private val cardWidth: Int = 480,
) : Container() {

    private var host: OverlayHostWidget? = null

    /** Called on Escape; defaults to closing the dialog. */
    var onCancel: () -> Unit = { close() }

    init {
        semantics = Semantics(
            role = Semantics.Role.DIALOG,
            name = title,
            actions = setOf(Semantics.Action.DISMISS),
        )
        addChild(body)
        actions.forEach { addChild(it) }
    }

    fun show(host: OverlayHostWidget) {
        this.host = host
        setBounds(host.bounds.x, host.bounds.y, host.bounds.width, host.bounds.height)
        host.showOverlay(this, modal = true)
    }

    fun close() {
        host?.removeOverlay(this)
        host = null
    }

    private fun cardRect(): Rectangle {
        val width = cardWidth.coerceAtMost(bounds.width - 32).coerceAtLeast(240)
        val height = HEADER_HEIGHT + bodyHeight + FOOTER_HEIGHT
        return Rectangle(
            bounds.x + (bounds.width - width) / 2,
            bounds.y + ((bounds.height - height) / 2).coerceAtLeast(16),
            width,
            height,
        )
    }

    // Positions children directly: a LayoutManager would stretch them to fill the whole scrim.
    override fun layout() {
        val card = cardRect()
        body.setBounds(card.x + PADDING, card.y + HEADER_HEIGHT, card.width - 2 * PADDING, bodyHeight)
        var right = card.x + card.width - PADDING
        val top = card.y + HEADER_HEIGHT + bodyHeight + 16
        for (button in actions.asReversed()) {
            val width = button.measure().width
            button.setBounds(right - width, top, width, ButtonWidget.HEIGHT)
            right -= width + BUTTON_GAP
        }
    }

    override fun onPaint(g: Graphics2D, localRegion: Rectangle) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = Color(0, 0, 0, 102)
        g.fillRect(0, 0, bounds.width, bounds.height)

        val origin = bounds
        val card = cardRect().also { it.translate(-origin.x, -origin.y) }
        g.color = composite(theme.surface, theme.elevationTints[3])
        g.fillRoundRect(card.x, card.y, card.width, card.height, CARD_RADIUS, CARD_RADIUS)
        g.font = theme.bodyFont.deriveFont(Font.PLAIN, 22f)
        g.color = theme.onSurface
        g.drawString(title, card.x + PADDING, card.y + 24 + g.fontMetrics.ascent)
    }

    override fun onMouseEvent(event: InputEvent.MouseInput): Boolean = event !is InputEvent.MouseMoved

    override fun onKeyEvent(event: InputEvent.KeyInput): Boolean {
        if (event is InputEvent.KeyPressed && event.keyCode == KeyEvent.VK_ESCAPE) {
            onCancel()
            return true
        }
        return false
    }

    companion object {
        private const val PADDING = 24
        private const val HEADER_HEIGHT = 72
        private const val FOOTER_HEIGHT = 16 + ButtonWidget.HEIGHT + 24
        private const val BUTTON_GAP = 8
        private const val CARD_RADIUS = 28
    }
}
