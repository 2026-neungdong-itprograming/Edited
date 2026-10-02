package io.github.nd2026.edited.core

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Framework-agnostic text editing core. Deliberately holds no reference to any UI toolkit -
 * it composes a [TextBuffer] (a [GapBufferTextBuffer] by default) rather than extending a
 * Swing/AWT component, so it can be driven by Swing, JavaFX, a headless linter, or a test
 * harness alike, matching any rendering system built on top of it.
 *
 * [TextBuffer] is pluggable so the storage strategy can be swapped (e.g. a memory-mapped
 * buffer for manuscripts too large to hold twice in memory) without touching this class.
 */
class TextArea(private val buffer: TextBuffer = GapBufferTextBuffer()) : AutoCloseable {

    constructor(initialText: String) : this(GapBufferTextBuffer(initialText))

    private val listeners = CopyOnWriteArrayList<TextAreaListener>()

    val length: Int get() = buffer.length
    val lineCount: Int get() = buffer.lineCount

    var caret: Int = 0
        private set

    var selectionAnchor: Int? = null
        private set

    fun addListener(listener: TextAreaListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: TextAreaListener) {
        listeners.remove(listener)
    }

    fun charAt(index: Int): Char = buffer.charAt(index)

    fun textInRange(start: Int, end: Int): String = buffer.subSequence(start, end)

    fun lineStart(line: Int): Int = buffer.lineStart(line)

    fun lineEnd(line: Int): Int = buffer.lineEnd(line)

    fun lineOf(offset: Int): Int = buffer.lineOf(offset)

    fun lineText(line: Int): String = buffer.subSequence(buffer.lineStart(line), buffer.lineEnd(line))

    fun snapshot(): String = buffer.subSequence(0, buffer.length)

    private val history = UndoManager()

    val canUndo: Boolean get() = history.canUndo
    val canRedo: Boolean get() = history.canRedo

    /**
     * Inserts [text] at [offset] and moves the caret after it. With [coalesce] the edit may merge
     * into the previous undo step (typing); leave it false for paste and programmatic edits.
     */
    fun insert(offset: Int, text: String, coalesce: Boolean = false) {
        if (text.isEmpty()) return
        val caretBefore = caret
        buffer.insert(offset, text)
        moveCaretTo(offset + text.length)
        notifyChanged(TextEdit.Insert(offset, text))
        history.record(TextEdit.Insert(offset, text), caretBefore, caret, coalesce)
    }

    fun delete(start: Int, end: Int, coalesce: Boolean = false) {
        if (start == end) return
        val caretBefore = caret
        val removed = buffer.subSequence(start, end)
        buffer.delete(start, end)
        moveCaretTo(start)
        notifyChanged(TextEdit.Delete(start, end, removed))
        history.record(TextEdit.Delete(start, end, removed), caretBefore, caret, coalesce)
    }

    /** Replacing is one undo step: the delete and the insert revert together. */
    fun replace(start: Int, end: Int, text: String) = compound {
        if (start != end) delete(start, end)
        if (text.isNotEmpty()) insert(start, text)
    }

    /**
     * Types [text] over the selection, or at the caret. Pass `coalesce = true` for keyboard and IME
     * input so a run of typing undoes as one step; a selection replaced this way is always its own step.
     */
    fun typeAtCaret(text: String, coalesce: Boolean = false) {
        val selection = selectionRange()?.takeUnless(IntRange::isEmpty)
        if (selection != null) {
            replace(selection.first, selection.last + 1, text)
        } else {
            insert(caret, text, coalesce)
        }
        clearSelection()
    }

    /** Every edit made inside [block] reverts and re-applies as a single undo step. */
    fun <T> compound(block: () -> T): T {
        history.beginGroup(caret)
        try {
            return block()
        } finally {
            history.endGroup(caret)
        }
    }

    /** Ends the current typing run, so the next keystroke starts a fresh undo step. */
    fun breakUndoGroup() = history.breakCoalescing()

    fun clearHistory() = history.clear()

    fun undo(): Boolean {
        val entry = history.takeUndo() ?: return false
        for (i in entry.edits.indices.reversed()) {
            when (val edit = entry.edits[i]) {
                is TextEdit.Insert -> applyDelete(edit.offset, edit.offset + edit.text.length)
                is TextEdit.Delete -> applyInsert(edit.start, edit.removedText)
            }
        }
        restoreCaret(entry.caretBefore)
        return true
    }

    fun redo(): Boolean {
        val entry = history.takeRedo() ?: return false
        for (edit in entry.edits) {
            when (edit) {
                is TextEdit.Insert -> applyInsert(edit.offset, edit.text)
                is TextEdit.Delete -> applyDelete(edit.start, edit.end)
            }
        }
        restoreCaret(entry.caretAfter)
        return true
    }

    private fun applyInsert(offset: Int, text: String) {
        buffer.insert(offset, text)
        notifyChanged(TextEdit.Insert(offset, text))
    }

    private fun applyDelete(start: Int, end: Int): String {
        val removed = buffer.subSequence(start, end)
        buffer.delete(start, end)
        // Keep the caret/anchor inside the document while an undo is still mid-way through its edits.
        if (caret > length) caret = length
        if ((selectionAnchor ?: 0) > length) selectionAnchor = length
        notifyChanged(TextEdit.Delete(start, end, removed))
        return removed
    }

    private fun restoreCaret(offset: Int) {
        val hadSelection = selectionAnchor != null
        moveCaretTo(offset)
        if (hadSelection) for (l in listeners) l.onSelectionChanged(null)
    }

    fun moveCaretTo(offset: Int, extendSelection: Boolean = false) {
        val clamped = offset.coerceIn(0, length)
        if (extendSelection) {
            if (selectionAnchor == null) selectionAnchor = caret
        } else {
            selectionAnchor = null
        }
        caret = clamped
        for (l in listeners) l.onCaretMoved(caret)
        if (extendSelection) {
            val range = selectionRange()
            for (l in listeners) l.onSelectionChanged(range)
        }
    }

    fun clearSelection() {
        if (selectionAnchor != null) {
            selectionAnchor = null
            for (l in listeners) l.onSelectionChanged(null)
        }
    }

    fun selectionRange(): IntRange? {
        val anchor = selectionAnchor ?: return null
        return if (anchor <= caret) anchor until caret else caret until anchor
    }

    fun visibleLineRange(viewport: Viewport): IntRange {
        val first = viewport.firstVisibleLine.coerceIn(0, lineCount)
        val last = (first + viewport.visibleLineCount).coerceIn(first, lineCount)
        return first until last
    }

    /** Only the text a renderer actually needs to paint - the point of virtual scrolling. */
    fun visibleText(viewport: Viewport): String {
        val range = visibleLineRange(viewport)
        if (range.isEmpty()) return ""
        val start = buffer.lineStart(range.first)
        val end = buffer.lineEnd(range.last)
        return buffer.subSequence(start, end)
    }

    private fun notifyChanged(edit: TextEdit) {
        for (l in listeners) l.onTextChanged(edit)
    }

    /** Releases the underlying buffer's resources if it holds any (e.g. off-heap memory). */
    override fun close() {
        if (buffer is AutoCloseable) buffer.close()
    }
}
