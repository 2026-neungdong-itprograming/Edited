package io.github.nd2026.edited.theme

import io.github.nd2026.edited.event.EventBus
import io.github.nd2026.edited.event.ToolkitEvent

data class ThemeChanged(val theme: Theme) : ToolkitEvent

/**
 * Holds the "current theme" for a widget tree. Widgets read [current] (via `Widget.theme`,
 * which walks up to the owning [io.github.nd2026.edited.ui.RootPane]'s provider) instead of
 * hardcoding colors/fonts. Changing [current] publishes [ThemeChanged] on [events] so attached
 * widgets can repaint themselves.
 */
class ThemeProvider(initial: Theme = MaterialLightTheme) {

    val events = EventBus()

    var current: Theme = initial
        set(value) {
            if (field == value) return
            field = value
            events.publish(ThemeChanged(value))
        }

    companion object {
        /** Fallback for widgets not yet attached to a [io.github.nd2026.edited.ui.RootPane]. */
        val default = ThemeProvider()
    }
}
