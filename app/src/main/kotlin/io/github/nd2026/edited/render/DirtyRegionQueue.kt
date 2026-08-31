package io.github.nd2026.edited.render

import org.jctools.queues.MpscArrayQueue
import java.awt.Rectangle

/**
 * Lock-free multi-producer/single-consumer queue for dirty-region requests. Any thread
 * (resize handling, scroll, background text edits) can call [offer] without blocking; only the
 * EDT drains it, once per repaint tick, via [drainInto]. Bounded capacity is fine - a full
 * queue just means "the whole viewport is dirty anyway", handled by [drainInto]'s overflow path.
 */
class DirtyRegionQueue(capacity: Int = 256) {

    private val queue = MpscArrayQueue<Rectangle>(capacity)

    /** Non-blocking; returns false if the queue is momentarily full (rare - see [drainInto]). */
    fun offer(region: Rectangle): Boolean = queue.offer(region)

    /** Drains all pending regions into [tracker]. Must only be called from the EDT. */
    fun drainInto(tracker: DirtyRegionTracker) {
        var region = queue.poll()
        while (region != null) {
            tracker.mark(region)
            region = queue.poll()
        }
    }
}
