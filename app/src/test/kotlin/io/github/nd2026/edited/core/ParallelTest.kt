package io.github.nd2026.edited.core

import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ParallelTest {
    @Test
    fun mapKeepsOrder() {
        val items = (0 until 10_000).toList()
        assertEquals(items.map { it * 2 }, Parallel.map(items) { it * 2 })
    }

    @Test
    fun smallInputsRunOnTheCallingThread() {
        val caller = Thread.currentThread()
        val threads = Parallel.map(listOf(1, 2, 3), minChunk = 8) { Thread.currentThread() }
        assertTrue(threads.all { it === caller })
    }

    @Test
    fun workIsSpreadOverWorkers() {
        if (Parallel.parallelism == 1) return
        val threads = Collections.synchronizedSet(HashSet<String>())
        Parallel.forEach((0 until 256).toList()) { threads += Thread.currentThread().name; Thread.sleep(2) }
        assertTrue(threads.size > 1, "only ran on $threads")
    }

    @Test
    fun nestedCallsShareThePoolWithoutDeadlock() {
        val calls = AtomicInteger()
        val sums = Parallel.map((0 until 64).toList()) { outer ->
            Parallel.map((0 until 64).toList()) { inner -> calls.incrementAndGet(); outer * inner }.sum()
        }
        assertEquals(64 * 64, calls.get())
        assertEquals((0 until 64).map { it * (0 until 64).sum() }, sums)
    }

    @Test
    fun exceptionsReachTheCaller() {
        assertFailsWith<IllegalStateException> {
            Parallel.map((0 until 1_000).toList()) { if (it == 777) error("boom") else it }
        }
    }
}
