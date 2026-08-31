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
        if (!widget.focusable || focused === widget) return
        focused = widget
    }

    fun clear() {
        focused = null
    }
}
