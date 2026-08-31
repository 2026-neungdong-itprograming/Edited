package io.github.nd2026.edited.ui.layout

import io.github.nd2026.edited.ui.Container

/** The single child (if any) fills the container's entire bounds. Extra children are stacked, unpositioned beyond the first. */
object FillLayout : LayoutManager {
    override fun layout(container: Container) {
        val b = container.bounds
        val child = container.children.firstOrNull() ?: return
        child.setBounds(b.x, b.y, b.width, b.height)
    }
}
