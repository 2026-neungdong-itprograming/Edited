package io.github.nd2026.edited.ui.layout

import io.github.nd2026.edited.ui.Container

/** Places every child over the same rectangle; child order is also paint z-order. */
object StackLayout : LayoutManager {
    override fun layout(container: Container) {
        val b = container.bounds
        for (child in container.children) child.setBounds(b.x, b.y, b.width, b.height)
    }
}
