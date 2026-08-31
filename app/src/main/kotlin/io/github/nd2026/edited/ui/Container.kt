package io.github.nd2026.edited.ui

import io.github.nd2026.edited.ui.layout.FillLayout
import io.github.nd2026.edited.ui.layout.LayoutManager

/** A [Widget] that arranges its children via a pluggable [LayoutManager]. */
open class Container(var layoutManager: LayoutManager = FillLayout) : Widget() {

    private val constraints = mutableMapOf<Widget, Any?>()

    /** Adds [child] with an optional layout-manager-specific constraint (e.g. a [io.github.nd2026.edited.ui.layout.BorderLayout.Region]). */
    fun addChild(child: Widget, constraint: Any?) {
        addChild(child)
        constraints[child] = constraint
    }

    fun constraintOf(child: Widget): Any? = constraints[child]

    override fun layout() {
        layoutManager.layout(this)
        for (child in children) child.layout()
    }
}
