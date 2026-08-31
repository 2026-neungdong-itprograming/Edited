package io.github.nd2026.edited.ui.layout

import io.github.nd2026.edited.ui.Container

/** Positions a [Container]'s children within its bounds. Kept minimal by design for this pass. */
fun interface LayoutManager {
    fun layout(container: Container)
}
