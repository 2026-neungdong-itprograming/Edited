package io.github.nd2026.edited.ui.docking

import io.github.nd2026.edited.event.InputEvent
import java.awt.Cursor
import java.awt.Graphics2D
import java.awt.Rectangle

/**
 * Drag handle between the two children of the split at [path]. Its hit target extends a few
 * pixels past the visible gap, and the ratio is clamped so neither side drops below
 * [DockHostWidget.MIN_PANE_SIZE].
 */
class DockSplitterWidget(
    private val host: DockHostWidget,
    val path: List<Int>,
    val axis: Axis,
) : io.github.nd2026.edited.ui.Widget() {
    /** Root-relative bounds of the whole split this handle divides. */
    private var splitArea = Rectangle()
    private var gap = Rectangle()

    internal fun place(gap: Rectangle, splitArea: Rectangle) {
        this.gap = Rectangle(gap)
        this.splitArea = Rectangle(splitArea)
        if (axis == Axis.HORIZONTAL) {
            setBounds(gap.x - HIT_SLOP, gap.y, gap.width + HIT_SLOP * 2, gap.height)
        } else {
            setBounds(gap.x, gap.y - HIT_SLOP, gap.width, gap.height + HIT_SLOP * 2)
        }
    }

    override fun onPaint(g: Graphics2D, localRegion: Rectangle) {
        if (!hovered && !pressed) return
        g.color = theme.primary
        if (axis == Axis.HORIZONTAL) g.fillRect(HIT_SLOP + gap.width / 2 - 1, 0, 2, bounds.height)
        else g.fillRect(0, HIT_SLOP + gap.height / 2 - 1, bounds.width, 2)
    }

    override fun onMouseEvent(event: InputEvent.MouseInput): Boolean = when (event) {
        is InputEvent.MouseEntered -> { setCursor(true); true }
        is InputEvent.MouseExited -> { if (!pressed) setCursor(false); true }
        is InputEvent.MousePressed -> event.button == 1
        is InputEvent.MouseDragged -> { host.resizeSplit(path, ratioAt(event.x, event.y)); true }
        is InputEvent.MouseReleased, is InputEvent.MouseCancelled -> {
            if (!bounds.contains(event.x, event.y)) setCursor(false)
            true
        }
        else -> false
    }

    /** The split ratio that puts the divider's centre at root-relative ([x], [y]). */
    fun ratioAt(x: Int, y: Int): Float {
        val horizontal = axis == Axis.HORIZONTAL
        val available = ((if (horizontal) splitArea.width else splitArea.height) - DockHostWidget.GAP).coerceAtLeast(1)
        val offset = (if (horizontal) x - splitArea.x else y - splitArea.y) - DockHostWidget.GAP / 2
        val min = if (available >= DockHostWidget.MIN_PANE_SIZE * 2) DockHostWidget.MIN_PANE_SIZE else 0
        val position = offset.coerceIn(min, available - min)
        return clampRatio(position.toFloat() / available)
    }

    private fun setCursor(active: Boolean) {
        hostPane?.cursor = if (!active) Cursor.getDefaultCursor()
        else Cursor.getPredefinedCursor(if (axis == Axis.HORIZONTAL) Cursor.E_RESIZE_CURSOR else Cursor.N_RESIZE_CURSOR)
    }

    companion object {
        private const val HIT_SLOP = 3
    }
}
