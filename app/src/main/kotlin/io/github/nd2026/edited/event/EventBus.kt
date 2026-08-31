package io.github.nd2026.edited.event

import java.util.concurrent.CopyOnWriteArrayList

/** Marker for toolkit-wide signals with no single natural owner, e.g. [ThemeChanged]. */
interface ToolkitEvent

/**
 * Lightweight tree-wide pub/sub for signals that don't have a natural parent-child owner (a
 * theme swap needs to reach every widget, not just one listener chain). Point-to-point signals
 * that *do* have a natural owner - e.g. text-changed/caret-moved - stay on
 * `core.TextAreaListener` instead of going through this bus.
 */
class EventBus {

    private val subscribers = CopyOnWriteArrayList<(ToolkitEvent) -> Unit>()

    fun subscribe(listener: (ToolkitEvent) -> Unit): () -> Unit {
        subscribers.add(listener)
        return { subscribers.remove(listener) }
    }

    fun publish(event: ToolkitEvent) {
        for (listener in subscribers) listener(event)
    }
}
