package io.github.nd2026.edited.ui

/**
 * Tracks which [Widget] currently has keyboard focus within one [RootPane], so [InputEvent]
 * key events can be routed to it. Lives in `ui` (not `event`) since it needs to know about
 * [Widget] directly and `event` must stay toolkit-widget-agnostic.
 */
class FocusManager {

    var focused: Widget? = null
        private set

    fun requestFocus(widget: Widget) {
        if (!widget.focusable || !widget.enabled || !widget.visible || focused === widget) return
        if (scope != null && !widget.isDescendantOf(scope!!)) return
        focused?.focused = false
        focused = widget
        widget.focused = true
    }

    fun clear() {
        focused?.focused = false
        focused = null
    }

    private var scope: Widget? = null

    fun setScope(widget: Widget?) {
        scope = widget
        if (widget != null && focused?.isDescendantOf(widget) != true) {
            clear()
            firstFocusable(widget)?.let(::requestFocus)
        }
    }

    fun moveFocus(root: Widget, backwards: Boolean = false): Boolean {
        val traversalRoot = scope ?: root
        val candidates = buildList { collectFocusable(traversalRoot, this) }
        if (candidates.isEmpty()) return false
        val current = candidates.indexOf(focused)
        val next = when {
            current < 0 && backwards -> candidates.lastIndex
            current < 0 -> 0
            backwards -> (current - 1 + candidates.size) % candidates.size
            else -> (current + 1) % candidates.size
        }
        requestFocus(candidates[next])
        return true
    }

    private fun firstFocusable(root: Widget): Widget? =
        buildList { collectFocusable(root, this) }.firstOrNull()

    private fun collectFocusable(widget: Widget, output: MutableList<Widget>) {
        if (!widget.visible || !widget.enabled) return
        if (widget.focusable) output += widget
        widget.children.forEach { collectFocusable(it, output) }
    }

    private fun Widget.isDescendantOf(ancestor: Widget): Boolean {
        var node: Widget? = this
        while (node != null) {
            if (node === ancestor) return true
            node = node.parent
        }
        return false
    }
}
