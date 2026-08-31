package io.github.nd2026.edited.render

import java.awt.Rectangle

/**
 * Accumulates dirty rectangles for one paint cycle and coalesces them so a burst of small
 * edits (e.g. fast typing) produces one clipped repaint instead of N. Uses a simple
 * union-if-overlap-or-adjacent heuristic rather than a full spatial index - dirty regions per
 * frame are typically few (single digits), so a linear scan is more than fast enough.
 */
class DirtyRegionTracker {

    private val regions = mutableListOf<Rectangle>()

    val isEmpty: Boolean get() = regions.isEmpty()

    fun mark(region: Rectangle) {
        if (region.isEmpty) return
        for (i in regions.indices) {
            val existing = regions[i]
            if (existing.intersects(region) || isAdjacent(existing, region)) {
                regions[i] = existing.union(region)
                return
            }
        }
        regions.add(Rectangle(region))
    }

    /** The current dirty rectangles, merged into their minimal covering set. */
    fun regions(): List<Rectangle> = regions.toList()

    /** The union of all dirty regions, or null if nothing is dirty. */
    fun union(): Rectangle? = regions.fold<Rectangle, Rectangle?>(null) { acc, r -> acc?.union(r) ?: Rectangle(r) }

    fun clear() {
        regions.clear()
    }

    private fun isAdjacent(a: Rectangle, b: Rectangle): Boolean {
        val expanded = Rectangle(a.x - 1, a.y - 1, a.width + 2, a.height + 2)
        return expanded.intersects(b)
    }
}
