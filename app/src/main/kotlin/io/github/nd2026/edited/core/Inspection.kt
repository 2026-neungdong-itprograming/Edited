package io.github.nd2026.edited.core

import java.util.concurrent.CopyOnWriteArrayList

/** Ordered by importance: later entries win when several markers share a line. */
enum class Severity { INFO, WARNING, ERROR }

/** A finding on a 0-based [line] of a [TextArea], produced by the linter/inspection engine. */
data class Inspection(val line: Int, val severity: Severity, val message: String)

/**
 * The inspections of one document, shared by the gutter (per-line icon) and the inspection stripe
 * (overview marker). Toolkit-agnostic: the linter publishes through [set]; widgets only read.
 *
 * [attachTo] keeps line numbers correct while the author types: lines inserted above a marker push it
 * down, and a marker on a line that was merged away is dropped, so markers don't drift between
 * linter runs.
 */
class InspectionModel {
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private var byLine: Map<Int, Inspection>? = null
    private var attached: Pair<TextArea, TextAreaListener>? = null

    var markers: List<Inspection> = emptyList()
        private set

    fun set(inspections: List<Inspection>) {
        markers = inspections.sortedWith(compareBy({ it.line }, { -it.severity.ordinal }))
        changed()
    }

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    /** The most severe inspection on [line], or null. */
    fun worstOnLine(line: Int): Inspection? {
        val index = byLine ?: markers.groupBy { it.line }
            .mapValues { (_, list) -> list.maxBy { it.severity.ordinal } }
            .also { byLine = it }
        return index[line]
    }

    fun attachTo(textArea: TextArea) {
        detach()
        val listener = object : TextAreaListener {
            override fun onTextChanged(edit: TextEdit) {
                if (markers.isEmpty()) return
                when (edit) {
                    is TextEdit.Insert -> shiftForInsert(textArea, edit)
                    is TextEdit.Delete -> shiftForDelete(textArea, edit)
                }
            }
        }
        textArea.addListener(listener)
        attached = textArea to listener
    }

    fun detach() {
        attached?.let { (area, listener) -> area.removeListener(listener) }
        attached = null
    }

    private fun shiftForInsert(textArea: TextArea, edit: TextEdit.Insert) {
        val added = edit.text.count { it == '\n' }
        if (added == 0) return
        val line = textArea.lineOf(edit.offset)
        // Breaking a line at column 0 pushes that whole line (and its marker) down.
        val first = if (edit.offset == textArea.lineStart(line)) line else line + 1
        markers = markers.map { if (it.line >= first) it.copy(line = it.line + added) else it }
        changed()
    }

    private fun shiftForDelete(textArea: TextArea, edit: TextEdit.Delete) {
        val removed = edit.removedText.count { it == '\n' }
        if (removed == 0) return
        val line = textArea.lineOf(edit.start)
        markers = markers.mapNotNull {
            when {
                it.line <= line -> it
                it.line <= line + removed -> null
                else -> it.copy(line = it.line - removed)
            }
        }
        changed()
    }

    private fun changed() {
        byLine = null
        for (l in listeners) l()
    }
}
