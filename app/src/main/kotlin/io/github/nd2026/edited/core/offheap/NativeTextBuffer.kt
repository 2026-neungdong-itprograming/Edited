package io.github.nd2026.edited.core.offheap

import io.github.nd2026.edited.core.IntArrayList
import io.github.nd2026.edited.core.TextBuffer
import java.lang.foreign.Arena

/**
 * Off-heap [TextBuffer] for very large documents (1M-10M+ chars). Same gap-buffer algorithm
 * and line-index strategy as `GapBufferTextBuffer`, but the character storage lives outside
 * the JVM heap in a [java.lang.foreign.MemorySegment] (via [NativeCharSegment]), so GC never
 * has to scan or copy the document text.
 *
 * Storage is UTF-16 (one [Char] per code unit), matching String/CharSequence semantics, so
 * charAt/subSequence stay O(1)/O(distance) instead of paying for UTF-8 decoding on every
 * access. At 10M chars that is ~20MB off-heap - negligible next to the GC-pressure it avoids.
 *
 * Owns an [Arena.ofShared] for its lifetime; callers MUST call [close] (or use the
 * `use { }` extension) when the buffer is no longer needed to release native memory - the JVM
 * heap and GC have no visibility into this allocation.
 */
class NativeTextBuffer(initialCapacity: Int = 64) : TextBuffer, AutoCloseable {

    private val arena: Arena = Arena.ofShared()
    private var storage = NativeCharSegment(arena, initialCapacity.coerceAtLeast(16))
    private var gapStart = 0
    private var gapEnd = storage.capacity
    private val lineStarts = IntArrayList().apply { add(0) }

    constructor(text: CharSequence) : this(text.length + 16) {
        insert(0, text)
    }

    override val length: Int
        get() = storage.capacity - (gapEnd - gapStart)

    override val lineCount: Int
        get() = lineStarts.size

    private fun toStorageIndex(docIndex: Int) =
        if (docIndex < gapStart) docIndex else docIndex + (gapEnd - gapStart)

    override fun charAt(index: Int): Char {
        require(index in 0 until length) { "index $index out of bounds for length $length" }
        return storage[toStorageIndex(index)]
    }

    override fun subSequence(start: Int, end: Int): String {
        require(start in 0..end && end <= length) { "invalid range [$start,$end) for length $length" }
        val result = CharArray(end - start)
        var written = 0
        var i = start
        while (i < end) {
            val storageIdx = toStorageIndex(i)
            val runEnd = if (i < gapStart) minOf(gapStart, end) else end
            val runLen = runEnd - i
            storage.copyOut(storageIdx, runLen, result, written)
            written += runLen
            i = runEnd
        }
        return String(result)
    }

    override fun lineStart(line: Int): Int {
        require(line in 0 until lineCount) { "line $line out of bounds for lineCount $lineCount" }
        return lineStarts[line]
    }

    override fun lineEnd(line: Int): Int {
        require(line in 0 until lineCount) { "line $line out of bounds for lineCount $lineCount" }
        return if (line == lineCount - 1) length else lineStarts[line + 1] - 1
    }

    override fun lineOf(offset: Int): Int {
        require(offset in 0..length) { "offset $offset out of bounds for length $length" }
        return lineStarts.floorIndex(offset)
    }

    private fun moveGapTo(pos: Int) {
        if (pos == gapStart) return
        if (pos < gapStart) {
            val count = gapStart - pos
            storage.copyWithin(pos, gapEnd - count, count)
            gapStart -= count
            gapEnd -= count
        } else {
            val count = pos - gapStart
            storage.copyWithin(gapEnd, gapStart, count)
            gapStart += count
            gapEnd += count
        }
    }

    private fun ensureGapCapacity(extra: Int) {
        val gapSize = gapEnd - gapStart
        if (gapSize >= extra) return
        val oldLength = length
        var newCapacity = storage.capacity * 2
        while (newCapacity - oldLength < extra) newCapacity *= 2

        val newStorage = NativeCharSegment(arena, newCapacity)
        val tailLength = storage.capacity - gapEnd
        storage.copyTo(newStorage, 0, 0, gapStart)
        val newGapEnd = newStorage.capacity - tailLength
        storage.copyTo(newStorage, gapEnd, newGapEnd, tailLength)

        gapEnd = newGapEnd
        storage = newStorage
    }

    override fun insert(offset: Int, text: CharSequence) {
        require(offset in 0..length) { "offset $offset out of bounds for length $length" }
        if (text.isEmpty()) return

        moveGapTo(offset)
        ensureGapCapacity(text.length)
        val chars = CharArray(text.length) { text[it] }
        storage.copyIn(chars, 0, gapStart, chars.size)
        gapStart += text.length

        val line = lineStarts.floorIndex(offset)
        for (i in line + 1 until lineStarts.size) lineStarts[i] = lineStarts[i] + text.length

        var newlineCount = 0
        for (c in text) if (c == '\n') newlineCount++
        if (newlineCount > 0) {
            val newStarts = IntArray(newlineCount)
            var w = 0
            for (i in text.indices) if (text[i] == '\n') newStarts[w++] = offset + i + 1
            lineStarts.addAllShiftedFrom(line + 1, newStarts)
        }
    }

    override fun delete(start: Int, end: Int) {
        require(start in 0..end && end <= length) { "invalid range [$start,$end) for length $length" }
        if (start == end) return

        moveGapTo(start)
        gapEnd += (end - start)

        val delta = end - start
        val fromIdx = maxOf(1, lineStarts.ceilingIndex(start))
        val toIdx = lineStarts.ceilingIndex(end)
        lineStarts.removeRange(fromIdx, toIdx)
        for (i in fromIdx until lineStarts.size) lineStarts[i] = lineStarts[i] - delta
    }

    override fun toString(): String = subSequence(0, length)

    /** Releases the off-heap memory backing this buffer. The buffer is unusable afterward. */
    override fun close() {
        arena.close()
    }
}
