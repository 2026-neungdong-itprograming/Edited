package io.github.nd2026.edited.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class GapBufferLineIndexTest {
    @Test
    fun backspaceOverNewlineMergesLines() {
        val buffer = GapBufferTextBuffer("a\nb")
        buffer.delete(1, 2)
        assertEquals(1, buffer.lineCount)
        assertEquals("ab", buffer.subSequence(buffer.lineStart(0), buffer.lineEnd(0)))
    }

    @Test
    fun lineIndexMatchesDocumentAfterRandomEdits() {
        val random = Random(42)
        val buffer = GapBufferTextBuffer()
        val model = StringBuilder()
        repeat(2000) {
            if (model.isEmpty() || random.nextInt(3) != 0) {
                val text = List(random.nextInt(1, 4)) { "ab\n"[random.nextInt(3)] }.joinToString("")
                val at = random.nextInt(model.length + 1)
                buffer.insert(at, text); model.insert(at, text)
            } else {
                val start = random.nextInt(model.length)
                val end = random.nextInt(start, minOf(model.length, start + 4)) + 1
                buffer.delete(start, end); model.delete(start, end)
            }
            val lines = model.toString().split('\n')
            assertEquals(lines.size, buffer.lineCount)
            lines.forEachIndexed { i, l -> assertEquals(l, buffer.subSequence(buffer.lineStart(i), buffer.lineEnd(i))) }
        }
    }
}
