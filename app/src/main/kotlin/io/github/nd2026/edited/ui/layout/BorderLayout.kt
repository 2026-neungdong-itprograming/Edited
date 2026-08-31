package io.github.nd2026.edited.ui.layout

import io.github.nd2026.edited.ui.Container

/**
 * Minimal north/south/east/west/center layout - enough to host a toolbar (north), a scrollbar
 * (east) and a text area (center). Not a general-purpose constraint solver by design; add a
 * richer layout only once a concrete widget actually needs one.
 */
class BorderLayout(
    private val northHeight: Int = 0,
    private val southHeight: Int = 0,
    private val eastWidth: Int = 0,
    private val westWidth: Int = 0,
) : LayoutManager {

    enum class Region { NORTH, SOUTH, EAST, WEST, CENTER }

    override fun layout(container: Container) {
        val b = container.bounds
        var top = b.y
        var bottom = b.y + b.height
        var left = b.x
        var right = b.x + b.width

        container.children.firstOrNull { container.constraintOf(it) == Region.NORTH }?.let {
            it.setBounds(left, top, right - left, northHeight)
            top += northHeight
        }
        container.children.firstOrNull { container.constraintOf(it) == Region.SOUTH }?.let {
            it.setBounds(left, bottom - southHeight, right - left, southHeight)
            bottom -= southHeight
        }
        container.children.firstOrNull { container.constraintOf(it) == Region.WEST }?.let {
            it.setBounds(left, top, westWidth, bottom - top)
            left += westWidth
        }
        container.children.firstOrNull { container.constraintOf(it) == Region.EAST }?.let {
            it.setBounds(right - eastWidth, top, eastWidth, bottom - top)
            right -= eastWidth
        }
        container.children.firstOrNull { container.constraintOf(it) == Region.CENTER }
            ?.setBounds(left, top, right - left, bottom - top)
    }
}
