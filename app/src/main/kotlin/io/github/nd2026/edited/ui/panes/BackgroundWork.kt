package io.github.nd2026.edited.ui.panes

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.SwingUtilities

/**
 * Runs a pane's slow computation (manuscript-wide morpheme analysis) off the EDT and applies the result
 * on the EDT, so painting and typing never wait on Kiwi. A result overtaken by a newer [run] or a
 * [cancel] is dropped. Lead-owned; panes only use it.
 */
internal class BackgroundWork {
    private val generation = AtomicInteger()
    private val pending = AtomicInteger()

    /** Computes [compute] in the background, then calls [apply] on the EDT unless something newer started meanwhile. */
    fun <T> run(compute: () -> T, apply: (T) -> Unit) {
        val mine = generation.incrementAndGet()
        pending.incrementAndGet()
        EXECUTOR.execute {
            val result = runCatching(compute)
            SwingUtilities.invokeLater {
                try {
                    if (mine == generation.get()) result.onSuccess(apply)
                } finally {
                    pending.decrementAndGet()
                }
            }
        }
    }

    /** Drops the result of anything still running. */
    fun cancel() {
        generation.incrementAndGet()
    }

    /** True while a [run] has not been applied (or dropped) yet. */
    val busy: Boolean get() = pending.get() > 0

    /** Waits until nothing is running or [timeoutMillis] passes; for tests, which have no event loop to wait on. */
    fun awaitIdle(timeoutMillis: Long = 30_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (busy) {
            if (System.currentTimeMillis() > deadline) return false
            Thread.sleep(5)
        }
        return true
    }

    private companion object {
        /** Coordinators only; the heavy lifting runs on `Parallel`'s pool, so a few idle threads are plenty. */
        val EXECUTOR = Executors.newCachedThreadPool { runnable -> Thread(runnable, "texted-pane").apply { isDaemon = true } }
    }
}
