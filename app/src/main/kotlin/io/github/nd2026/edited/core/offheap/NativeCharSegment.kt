package io.github.nd2026.edited.core.offheap

import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout

private const val CHAR_BYTES = 2L

/**
 * Growable off-heap char storage backed by a single [Arena]. Growing allocates a new, larger
 * segment and copies the live content into it; the old segment is not freed individually - it
 * becomes garbage within the arena and is only reclaimed when the whole arena is closed (see
 * [NativeTextBuffer.close]). This trades a bit of transient off-heap memory during growth
 * spikes for a much simpler ownership model than per-segment lifetime tracking.
 */
internal class NativeCharSegment(private val arena: Arena, initialCapacity: Int) {

    var capacity: Int = initialCapacity.coerceAtLeast(16)
        private set

    private var segment: MemorySegment = arena.allocate(capacity.toLong() * CHAR_BYTES, CHAR_BYTES)

    operator fun get(index: Int): Char = segment.getAtIndex(ValueLayout.JAVA_CHAR, index.toLong())

    operator fun set(index: Int, value: Char) {
        segment.setAtIndex(ValueLayout.JAVA_CHAR, index.toLong(), value)
    }

    /** memmove-style copy within this segment; correctly handles overlapping ranges. */
    fun copyWithin(srcIndex: Int, dstIndex: Int, length: Int) {
        if (length <= 0) return
        MemorySegment.copy(segment, srcIndex * CHAR_BYTES, segment, dstIndex * CHAR_BYTES, length * CHAR_BYTES)
    }

    fun copyTo(target: NativeCharSegment, srcIndex: Int, dstIndex: Int, length: Int) {
        if (length <= 0) return
        MemorySegment.copy(segment, srcIndex * CHAR_BYTES, target.segment, dstIndex * CHAR_BYTES, length * CHAR_BYTES)
    }

    fun copyOut(srcIndex: Int, length: Int, dest: CharArray, destOffset: Int) {
        if (length <= 0) return
        MemorySegment.copy(segment, ValueLayout.JAVA_CHAR, srcIndex.toLong(), dest, destOffset, length)
    }

    fun copyIn(source: CharArray, srcOffset: Int, dstIndex: Int, length: Int) {
        if (length <= 0) return
        MemorySegment.copy(source, srcOffset, segment, ValueLayout.JAVA_CHAR, dstIndex.toLong(), length)
    }
}
