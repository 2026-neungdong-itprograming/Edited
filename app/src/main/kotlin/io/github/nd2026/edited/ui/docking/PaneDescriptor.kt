package io.github.nd2026.edited.ui.docking

import io.github.nd2026.edited.ui.Widget

/**
 * Describes one dockable pane. [content] is created once and moved between docked groups and
 * floating windows instead of being recreated, so pane state (scroll, selection) survives moves.
 *
 * [defaultEdge] is where the pane lives in the default layout and where it returns to when it is
 * restored from a strip or re-docked from a floating window; null means the central editor area.
 * A [required] pane that is missing from a saved layout is merged back at its default position.
 */
class PaneDescriptor(
    val id: PaneId,
    val title: String,
    val shortTitle: String = title.take(1),
    val defaultEdge: DockEdge? = null,
    val closable: Boolean = false,
    val hideable: Boolean = defaultEdge != null,
    val required: Boolean = true,
    val content: Widget,
)

/** Looks up [PaneDescriptor]s by id, preserving registration order. */
class PaneRegistry(descriptors: List<PaneDescriptor> = emptyList()) {
    private val byId = linkedMapOf<PaneId, PaneDescriptor>()

    init {
        descriptors.forEach(::register)
    }

    fun register(descriptor: PaneDescriptor) {
        require(descriptor.id !in byId) { "duplicate pane: ${descriptor.id}" }
        byId[descriptor.id] = descriptor
    }

    operator fun get(id: PaneId): PaneDescriptor? = byId[id]
    fun require(id: PaneId): PaneDescriptor = byId[id] ?: throw IllegalArgumentException("unknown pane: $id")
    operator fun contains(id: PaneId): Boolean = id in byId
    val ids: Set<PaneId> get() = byId.keys
    val all: Collection<PaneDescriptor> get() = byId.values
}
