package io.github.nd2026.edited.core

/**
 * Growable primitive-int array used for line-start indices. Kept as a raw IntArray (no
 * boxing) since it is touched on every edit. Shared by both the on-heap gap buffer and the
 * off-heap native buffer so their line-indexing logic stays identical.
 */
internal class IntArrayList(initialCapacity: Int = 16) {
    var data = IntArray(initialCapacity)
        private set
    var size = 0
        private set

    operator fun get(index: Int) = data[index]
    operator fun set(index: Int, value: Int) {
        data[index] = value
    }

    private fun ensureCapacity(min: Int) {
        if (data.size < min) {
            var newCap = data.size * 2
            if (newCap < min) newCap = min
            data = data.copyOf(newCap)
        }
    }

    fun add(value: Int) {
        ensureCapacity(size + 1)
        data[size++] = value
    }

    fun insertAt(index: Int, value: Int) {
        ensureCapacity(size + 1)
        System.arraycopy(data, index, data, index + 1, size - index)
        data[index] = value
        size++
    }

    fun removeRange(fromIndex: Int, toIndex: Int) {
        if (fromIndex >= toIndex) return
        System.arraycopy(data, toIndex, data, fromIndex, size - toIndex)
        size -= (toIndex - fromIndex)
    }

    fun addAllShiftedFrom(index: Int, values: IntArray) {
        if (values.isEmpty()) return
        ensureCapacity(size + values.size)
        System.arraycopy(data, index, data, index + values.size, size - index)
        System.arraycopy(values, 0, data, index, values.size)
        size += values.size
    }

    /** Largest index i with data[i] <= key, restricted to [0, size). */
    fun floorIndex(key: Int): Int {
        var lo = 0
        var hi = size - 1
        var result = 0
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (data[mid] <= key) {
                result = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return result
    }

    /** Smallest index i with data[i] >= key, restricted to [0, size). */
    fun ceilingIndex(key: Int): Int {
        var lo = 0
        var hi = size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (data[mid] < key) lo = mid + 1 else hi = mid
        }
        return lo
    }
}
