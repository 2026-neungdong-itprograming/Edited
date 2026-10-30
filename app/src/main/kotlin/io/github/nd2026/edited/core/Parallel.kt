package io.github.nd2026.edited.core

import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ForkJoinTask
import java.util.concurrent.RecursiveAction
import java.util.concurrent.atomic.AtomicInteger

/**
 * One process-wide work-stealing pool for CPU-bound work that splits naturally (chapters, lines).
 * Lead-owned; features only call [map]/[forEach].
 *
 * Every parallel call in the app shares this pool, so nesting is safe and cheap: a chapter-level
 * [map] whose items each call a line-level [map] (relint → `Linter.run` → `Morphology.analyzeLines`)
 * just forks more tasks onto the same workers, and a worker waiting on a join steals queued work
 * instead of blocking. There is never more than [parallelism] busy threads, however deep it nests.
 *
 * One core is left free for the EDT, so heavy background work cannot starve typing and painting.
 */
object Parallel {
    val parallelism: Int = (Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(1)

    // The worker's own poolIndex is only assigned once it starts, so number the threads here.
    private val threadCount = AtomicInteger()

    val pool: ForkJoinPool = ForkJoinPool(
        parallelism,
        { pool -> ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool).apply { name = "texted-worker-${threadCount.getAndIncrement()}"; isDaemon = true } },
        null,
        false,
    )

    /**
     * [transform] applied to every item, results in the same order as [items]. Runs on the calling thread
     * when there are fewer than [minChunk] items (forking a handful of tiny jobs costs more than it saves).
     * The first exception thrown by [transform] is rethrown here.
     */
    fun <T, R> map(items: List<T>, minChunk: Int = 1, transform: (T) -> R): List<R> {
        if (items.size <= minChunk.coerceAtLeast(1) || parallelism == 1) return items.map(transform)
        val results = arrayOfNulls<Any?>(items.size)
        // ~4 chunks per worker keeps every core busy even when items take very different times (long chapters).
        val grain = maxOf(minChunk, items.size / (parallelism * 4), 1)
        run(Chunk(0, items.size, grain) { i -> results[i] = transform(items[i]) })
        @Suppress("UNCHECKED_CAST")
        return results.asList() as List<R>
    }

    /** [action] on every item, in no particular order; see [map]. */
    fun <T> forEach(items: List<T>, minChunk: Int = 1, action: (T) -> Unit) {
        if (items.size <= minChunk.coerceAtLeast(1) || parallelism == 1) return items.forEach(action)
        run(Chunk(0, items.size, maxOf(minChunk, items.size / (parallelism * 4), 1)) { i -> action(items[i]) })
    }

    /** Joins in place when already on a worker (nested call), otherwise hands the task to the pool and waits. */
    private fun run(task: ForkJoinTask<*>) {
        if (ForkJoinTask.inForkJoinPool() && ForkJoinTask.getPool() === pool) task.invoke() else pool.invoke(task)
    }

    /** Halves [from, to) until a piece is at most [grain] long; each piece runs [body] per index. */
    private class Chunk(val from: Int, val to: Int, val grain: Int, val body: (Int) -> Unit) : RecursiveAction() {
        override fun compute() {
            if (to - from <= grain) {
                for (i in from until to) body(i)
                return
            }
            val mid = (from + to) ushr 1
            invokeAll(Chunk(from, mid, grain, body), Chunk(mid, to, grain, body))
        }
    }
}
