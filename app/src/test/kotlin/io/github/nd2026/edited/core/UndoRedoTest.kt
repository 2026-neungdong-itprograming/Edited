package io.github.nd2026.edited.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UndoRedoTest {
    private fun type(area: TextArea, text: String) = text.forEach { area.typeAtCaret(it.toString(), coalesce = true) }

    @Test
    fun undoAndRedoRestoreTextAndCaret() {
        val area = TextArea("abc")
        area.moveCaretTo(3)
        area.insert(3, "def")
        area.undo()
        assertEquals("abc", area.snapshot())
        assertEquals(3, area.caret)
        area.redo()
        assertEquals("abcdef", area.snapshot())
        assertEquals(6, area.caret)
    }

    @Test
    fun typingRunUndoesOneWordAtATime() {
        val area = TextArea()
        type(area, "hello world")
        area.undo()
        assertEquals("hello ", area.snapshot())
        area.undo()
        assertEquals("", area.snapshot())
        assertFalse(area.canUndo)
    }

    @Test
    fun newlineEndsTheTypingRun() {
        val area = TextArea()
        type(area, "ab\ncd")
        area.undo()
        assertEquals("ab\n", area.snapshot())
        area.undo()
        assertEquals("ab", area.snapshot())
    }

    @Test
    fun imeCommitsOfOneWordMergeAndCommitsAfterASpaceDoNot() {
        // Korean IMEs commit one syllable at a time: "안", "녕", " ", "하", "세", "요".
        val area = TextArea()
        listOf("안", "녕", " ", "하", "세", "요").forEach { area.typeAtCaret(it, coalesce = true) }
        area.undo()
        assertEquals("안녕 ", area.snapshot())
        area.undo()
        assertEquals("", area.snapshot())
    }

    @Test
    fun imeCommitOverASelectionIsOneStep() {
        val area = TextArea("가나다라")
        area.moveCaretTo(1)
        area.moveCaretTo(3, extendSelection = true)
        area.typeAtCaret("한", coalesce = true)
        assertEquals("가한라", area.snapshot())
        area.undo()
        assertEquals("가나다라", area.snapshot())
        area.redo()
        assertEquals("가한라", area.snapshot())
    }

    @Test
    fun committedSelectionReplacementDoesNotMergeWithFollowingTyping() {
        val area = TextArea("abc")
        area.moveCaretTo(0)
        area.moveCaretTo(3, extendSelection = true)
        area.typeAtCaret("x", coalesce = true)
        area.typeAtCaret("y", coalesce = true)
        area.undo()
        assertEquals("x", area.snapshot())
        area.undo()
        assertEquals("abc", area.snapshot())
    }

    @Test
    fun consecutiveBackspacesMergeIntoOneStep() {
        val area = TextArea("hello")
        area.moveCaretTo(5)
        repeat(3) { area.delete(area.caret - 1, area.caret, coalesce = true) }
        assertEquals("he", area.snapshot())
        area.undo()
        assertEquals("hello", area.snapshot())
        assertEquals(5, area.caret)
    }

    @Test
    fun consecutiveForwardDeletesMergeIntoOneStep() {
        val area = TextArea("hello")
        area.moveCaretTo(0)
        repeat(3) { area.delete(0, 1, coalesce = true) }
        assertEquals("lo", area.snapshot())
        area.undo()
        assertEquals("hello", area.snapshot())
    }

    @Test
    fun breakingTheGroupStartsANewStep() {
        val area = TextArea()
        type(area, "ab")
        area.breakUndoGroup()
        type(area, "cd")
        area.undo()
        assertEquals("ab", area.snapshot())
    }

    @Test
    fun compoundGroupsEditsIntoOneStep() {
        val area = TextArea("one two")
        area.compound {
            area.delete(0, 3)
            area.insert(0, "1")
            area.insert(area.length, "!")
        }
        assertEquals("1 two!", area.snapshot())
        area.undo()
        assertEquals("one two", area.snapshot())
        assertFalse(area.canUndo)
    }

    @Test
    fun newEditClearsRedoHistory() {
        val area = TextArea()
        area.insert(0, "a")
        area.undo()
        assertTrue(area.canRedo)
        area.insert(0, "b")
        assertFalse(area.canRedo)
        assertFalse(area.redo())
    }

    @Test
    fun undoNotifiesListenersSoIndexesStayInSync() {
        val area = TextArea("a\nb")
        val seen = mutableListOf<TextEdit>()
        area.addListener(object : TextAreaListener {
            override fun onTextChanged(edit: TextEdit) { seen += edit }
        })
        area.delete(1, 2)
        area.undo()
        assertEquals(listOf<TextEdit>(TextEdit.Delete(1, 2, "\n"), TextEdit.Insert(1, "\n")), seen)
        assertEquals(2, area.lineCount)
    }

    @Test
    fun historyIsBoundedByEntryCount() {
        val area = TextArea()
        repeat(5_000) { area.insert(area.length, "x ") } // a space ends each run, so every insert is its own step
        var undone = 0
        while (area.undo()) undone++
        assertTrue(undone <= 2_000, "undone=$undone")
    }

    @Test
    fun randomEditsFullyUndoAndRedo() {
        val random = Random(7)
        val area = TextArea("seed text\nsecond line")
        val original = area.snapshot()
        repeat(500) {
            val len = area.length
            when (random.nextInt(5)) {
                0 -> area.insert(random.nextInt(len + 1), "ab\n한"[random.nextInt(4)].toString(), coalesce = random.nextBoolean())
                1 -> area.insert(random.nextInt(len + 1), "xyz\nw")
                2 -> if (len > 0) { val s = random.nextInt(len); area.delete(s, minOf(len, s + 1 + random.nextInt(3))) }
                3 -> if (len > 1) { val s = random.nextInt(len - 1); area.replace(s, s + 1, "Q") }
                else -> area.breakUndoGroup()
            }
        }
        val final = area.snapshot()
        while (area.undo()) Unit
        assertEquals(original, area.snapshot())
        while (area.redo()) Unit
        assertEquals(final, area.snapshot())
    }
}
