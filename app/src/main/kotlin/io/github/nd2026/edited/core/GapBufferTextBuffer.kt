package io.github.nd2026.edited.core

/**
 * Gap-buffer backed [TextBuffer].
 *
 * Edits cluster around a single moving cursor in a text editor, so a gap buffer gives
 * amortized O(1) insert/delete at the gap and only pays O(distance moved) when the
 * cursor jumps - far cheaper than reallocating/copying the whole document on every
 * keystroke, which is what a naive immutable-String model would do.
 *
 * Line offsets are tracked separately in an [IntArrayList] so line/column lookups used
 * for virtual scrolling never require scanning the document.
 */
class GapBufferTextBuffer(initialCapacity: Int = 64) : TextBuffer {

    private var buffer = CharArray(initialCapacity.coerceAtLeast(16))
    private var gapStart = 0
    private var gapEnd = buffer.size
    private val lineStarts = IntArrayList().apply { add(0) }

    constructor(text: CharSequence) : this(text.length + 16) {
        insert(0, text)
    }

    override val length: Int
        get() = buffer.size - (gapEnd - gapStart)

    override val lineCount: Int
        get() = lineStarts.size

    private fun toBufferIndex(docIndex: Int) =
        if (docIndex < gapStart) docIndex else docIndex + (gapEnd - gapStart)

    override fun charAt(index: Int): Char {
        require(index in 0 until length) { "index $index out of bounds for length $length" }
        return buffer[toBufferIndex(index)]
    }

    override fun subSequence(start: Int, end: Int): String {
        require(start in 0..end && end <= length) { "invalid range [$start,$end) for length $length" }
        val result = CharArray(end - start)
        var written = 0
        var i = start
        while (i < end) {
            val bufIdx = toBufferIndex(i)
            val runEnd = if (i < gapStart) minOf(gapStart, end) else end
            val runLen = runEnd - i
            System.arraycopy(buffer, bufIdx, result, written, runLen)
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
            System.arraycopy(buffer, pos, buffer, gapEnd - count, count)
            gapStart -= count
            gapEnd -= count
        } else {
            val count = pos - gapStart
            System.arraycopy(buffer, gapEnd, buffer, gapStart, count)
            gapStart += count
            gapEnd += count
        }
    }

    private fun ensureGapCapacity(extra: Int) {
        val gapSize = gapEnd - gapStart
        if (gapSize >= extra) return
        val oldLength = length
        var newCapacity = buffer.size * 2
        while (newCapacity - oldLength < extra) newCapacity *= 2
        val newBuffer = CharArray(newCapacity)
        val tailLength = buffer.size - gapEnd
        System.arraycopy(buffer, 0, newBuffer, 0, gapStart)
        System.arraycopy(buffer, gapEnd, newBuffer, newBuffer.size - tailLength, tailLength)
        gapEnd = newBuffer.size - tailLength
        buffer = newBuffer
    }

    override fun insert(offset: Int, text: CharSequence) {
        require(offset in 0..length) { "offset $offset out of bounds for length $length" }
        if (text.isEmpty()) return

        moveGapTo(offset)
        ensureGapCapacity(text.length)
        for (i in text.indices) buffer[gapStart + i] = text[i]
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
}
