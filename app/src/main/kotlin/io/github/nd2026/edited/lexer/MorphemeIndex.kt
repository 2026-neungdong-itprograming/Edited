package io.github.nd2026.edited.lexer

import io.github.nd2026.edited.core.TextArea
import io.github.nd2026.edited.core.TextAreaListener
import io.github.nd2026.edited.core.TextEdit
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * Keeps a per-line cache of [Morpheme]s in sync with a [TextArea], re-analyzing only the lines
 * an edit actually touched instead of the whole document - the incremental design
 * [io.github.nd2026.edited.core.TextEdit]'s KDoc calls for.
 *
 * Cached morphemes are stored with offsets *relative to their line*, not shifted to absolute
 * document offsets, because an edit anywhere before a line shifts that line's absolute start
 * without changing its content or requiring re-analysis; baking in absolute offsets at analysis
 * time would make them stale the moment an earlier line changes. [morphemesForLine] shifts them
 * to the current absolute position on read instead, which is a cheap [TextArea.lineStart]
 * lookup rather than a re-tokenization.
 *
 * Edits only ever arrive on the EDT (Swing dispatches key events there), so the dirty-line set
 * needs no cross-thread synchronization - unlike
 * [io.github.nd2026.edited.render.DirtyRegionQueue], which exists because repaint requests can
 * come from any thread. A [Timer] debounces bursts of edits (fast typing) before handing the
 * batch to a single background thread that owns the (not thread-safe) [MorphemeAnalyzer].
 * Each job captures the line's text at scheduling time; when its result comes back on the EDT,
 * it's applied only if that line's text still matches the snapshot, so a result overtaken by a
 * newer edit is silently dropped instead of corrupting the cache.
 */
class MorphemeIndex(
    private val textArea: TextArea,
    private val analyzer: MorphemeAnalyzer,
    debounceMillis: Int = 150,
) : TextAreaListener, AutoCloseable {

    private val cache = MutableList<List<Morpheme>?>(textArea.lineCount) { null }
    private val dirtyLines = mutableSetOf<Int>()
    private val listeners = CopyOnWriteArrayList<MorphemeIndexListener>()

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "nori-lexer").apply { isDaemon = true }
    }
    private val debounce = Timer(debounceMillis) { scheduleAnalysis() }.apply { isRepeats = false }

    init {
        textArea.addListener(this)
        for (line in cache.indices) dirtyLines += line
        scheduleAnalysis()
    }

    fun addListener(listener: MorphemeIndexListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: MorphemeIndexListener) {
        listeners.remove(listener)
    }

    /** Morphemes for [line] shifted to current absolute document offsets, or null if [line]
     *  hasn't been analyzed yet (e.g. it was just edited and the debounce hasn't fired). */
    fun morphemesForLine(line: Int): List<Morpheme>? =
        cache.getOrNull(line)?.shiftedBy(textArea.lineStart(line))

    override fun onTextChanged(edit: TextEdit) {
        when (edit) {
            is TextEdit.Insert -> onInsert(edit)
            is TextEdit.Delete -> onDelete(edit)
        }
        debounce.restart()
    }

    private fun onInsert(edit: TextEdit.Insert) {
        val startLine = textArea.lineOf(edit.offset)
        val newLines = edit.text.count { it == '\n' }
        repeat(newLines) { cache.add(startLine + 1, null) }
        for (line in startLine..(startLine + newLines)) markDirty(line)
    }

    private fun onDelete(edit: TextEdit.Delete) {
        val startLine = textArea.lineOf(edit.start)
        val removedLines = edit.removedText.count { it == '\n' }
        repeat(removedLines) { if (startLine + 1 < cache.size) cache.removeAt(startLine + 1) }
        markDirty(startLine)
    }

    private fun markDirty(line: Int) {
        if (line in cache.indices) dirtyLines += line
    }

    private fun scheduleAnalysis() {
        if (dirtyLines.isEmpty()) return
        val jobs = dirtyLines.filter { it in cache.indices }.map { it to textArea.lineText(it) }
        dirtyLines.clear()
        executor.submit { analyzeAndPublish(jobs) }
    }

    private fun analyzeAndPublish(jobs: List<Pair<Int, String>>) {
        val results = jobs.map { (line, snapshot) -> Triple(line, snapshot, analyzer.analyze(snapshot)) }
        SwingUtilities.invokeLater { applyResults(results) }
    }

    private fun applyResults(results: List<Triple<Int, String, List<Morpheme>>>) {
        for ((line, snapshot, morphemes) in results) {
            if (line !in cache.indices) continue
            if (textArea.lineText(line) != snapshot) continue
            cache[line] = morphemes
            val absolute = morphemes.shiftedBy(textArea.lineStart(line))
            for (l in listeners) l.onLineAnalyzed(line, absolute)
        }
    }

    override fun close() {
        textArea.removeListener(this)
        debounce.stop()
        executor.shutdownNow()
    }
}

private fun List<Morpheme>.shiftedBy(delta: Int): List<Morpheme> = map { it.shiftedBy(delta) }

private fun Morpheme.shiftedBy(delta: Int): Morpheme = copy(
    start = start + delta,
    end = end + delta,
    decompound = decompound.shiftedBy(delta),
)
