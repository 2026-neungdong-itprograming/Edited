package io.github.nd2026.edited.core

/** One undoable step: every [edits] element is reverted/re-applied together. */
class UndoEntry internal constructor(
    internal val edits: MutableList<TextEdit>,
    val caretBefore: Int,
    var caretAfter: Int,
    internal var mergeable: Boolean,
) {
    internal fun weight(): Int = edits.sumOf { edit ->
        when (edit) {
            is TextEdit.Insert -> edit.text.length
            is TextEdit.Delete -> edit.removedText.length
        }
    }
}

/**
 * Undo/redo history for a [TextArea]; holds no text itself, only the [TextEdit]s to replay.
 *
 * Two mechanisms decide what one Ctrl+Z reverts:
 *  - **Compound groups** ([beginGroup]/[endGroup]): every edit recorded in between is one step.
 *    A selection replaced by typed or IME-committed text (delete + insert) is therefore one step.
 *  - **Coalescing** ([record] with `coalesce = true`): consecutive typing/backspacing that touches
 *    adjacent text merges into the previous step. A merge never crosses a newline, and a run ends
 *    after whitespace, so undo reverts roughly one word at a time. Korean IME commits arrive one
 *    syllable at a time and are merged by this same rule, so a word is a single step instead of
 *    one per syllable.
 *
 * The history is bounded by [maxEntries] and by [maxChars] of stored text (a large deleted
 * selection keeps its text alive), oldest steps dropped first.
 */
class UndoManager(
    private val maxEntries: Int = 2_000,
    private val maxChars: Int = 20_000_000,
) {
    private val undoStack = ArrayDeque<UndoEntry>()
    private val redoStack = ArrayDeque<UndoEntry>()
    private var group: UndoEntry? = null
    private var groupDepth = 0
    private var storedChars = 0L

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val undoCount: Int get() = undoStack.size
    val redoCount: Int get() = redoStack.size

    fun beginGroup(caret: Int) {
        if (groupDepth++ == 0) group = UndoEntry(mutableListOf(), caret, caret, mergeable = false)
    }

    fun endGroup(caret: Int) {
        check(groupDepth > 0) { "endGroup without beginGroup" }
        if (--groupDepth > 0) return
        val finished = group ?: return
        group = null
        if (finished.edits.isEmpty()) return
        finished.caretAfter = caret
        push(finished)
    }

    fun record(edit: TextEdit, caretBefore: Int, caretAfter: Int, coalesce: Boolean) {
        redoClear()
        group?.let {
            it.edits += edit
            return
        }
        val top = undoStack.lastOrNull()
        if (coalesce && top != null && top.mergeable && top.edits.size == 1) {
            val merged = merge(top.edits[0], edit)
            if (merged != null) {
                storedChars += weightOf(merged) - weightOf(top.edits[0])
                top.edits[0] = merged
                top.caretAfter = caretAfter
                return
            }
        }
        push(UndoEntry(mutableListOf(edit), caretBefore, caretAfter, mergeable = coalesce))
    }

    /** The next edit starts a new step even if it would be adjacent to the previous one. */
    fun breakCoalescing() {
        undoStack.lastOrNull()?.mergeable = false
    }

    internal fun takeUndo(): UndoEntry? {
        val entry = undoStack.removeLastOrNull() ?: return null
        storedChars -= entry.weight()
        redoStack.addLast(entry)
        return entry
    }

    internal fun takeRedo(): UndoEntry? {
        val entry = redoStack.removeLastOrNull() ?: return null
        entry.mergeable = false
        undoStack.addLast(entry)
        storedChars += entry.weight()
        return entry
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
        group = null
        groupDepth = 0
        storedChars = 0
    }

    private fun redoClear() {
        redoStack.clear()
    }

    private fun push(entry: UndoEntry) {
        undoStack.addLast(entry)
        storedChars += entry.weight()
        while (undoStack.size > 1 && (undoStack.size > maxEntries || storedChars > maxChars)) {
            storedChars -= undoStack.removeFirst().weight()
        }
    }

    private fun weightOf(edit: TextEdit): Int = when (edit) {
        is TextEdit.Insert -> edit.text.length
        is TextEdit.Delete -> edit.removedText.length
    }

    private fun merge(previous: TextEdit, next: TextEdit): TextEdit? = when {
        previous is TextEdit.Insert && next is TextEdit.Insert ->
            if (next.offset == previous.offset + previous.text.length &&
                '\n' !in previous.text && '\n' !in next.text &&
                !previous.text.last().isWhitespace()
            ) TextEdit.Insert(previous.offset, previous.text + next.text) else null

        previous is TextEdit.Delete && next is TextEdit.Delete ->
            if ('\n' in previous.removedText || '\n' in next.removedText) null
            else when {
                // Backspace: each delete ends where the previous one began.
                next.end == previous.start ->
                    TextEdit.Delete(next.start, previous.end, next.removedText + previous.removedText)
                // Forward delete: each delete starts where the previous one did.
                next.start == previous.start ->
                    TextEdit.Delete(previous.start, previous.end + (next.end - next.start), previous.removedText + next.removedText)
                else -> null
            }

        else -> null
    }
}
