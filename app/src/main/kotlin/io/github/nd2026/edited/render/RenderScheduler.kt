package io.github.nd2026.edited.render

import java.awt.Rectangle
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JComponent
import javax.swing.SwingUtilities

/**
 * Coalesces repaint requests from anywhere (EDT or background threads) into at most one
 * `repaint()` per EDT tick, via [DirtyRegionQueue] (lock-free enqueue) + [DirtyRegionTracker]
 * (region merging). This is what lets N edits or scroll events in one tick produce a single
 * paint pass instead of N, and is the piece [io.github.nd2026.edited.ui.Widget]s call into
 * instead of invoking Swing's `repaint()` directly.
 */
class RenderScheduler(private val target: JComponent) {

    private val queue = DirtyRegionQueue()
    private val tracker = DirtyRegionTracker()
    private val flushScheduled = AtomicBoolean(false)

    /** Safe to call from any thread. */
    fun requestRepaint(region: Rectangle) {
        if (!queue.offer(region)) {
            // Queue momentarily full: fall back to "the whole component is dirty" rather than
            // dropping the request, since correctness must never depend on queue capacity.
            requestRepaint(Rectangle(0, 0, target.width, target.height))
            return
        }
        scheduleFlush()
    }

    private fun scheduleFlush() {
        if (flushScheduled.compareAndSet(false, true)) {
            SwingUtilities.invokeLater(::flush)
        }
    }

    private fun flush() {
        flushScheduled.set(false)
        queue.drainInto(tracker)
        for (region in tracker.regions()) target.repaint(region)
        tracker.clear()
    }
}
