package io.github.nd2026.edited.ui

import java.awt.Rectangle

/** Contract used by [RootPane] to bridge AWT input methods into a toolkit-owned editor. */
interface TextInputClient {
    fun updateComposition(committedText: String, composedText: String, caretInComposition: Int): Boolean
    fun updateCompositionCaret(caretInComposition: Int)
    fun inputMethodTextLocation(offsetInComposition: Int): Rectangle
    fun inputMethodInsertOffset(): Int
    fun committedText(beginIndex: Int, endIndex: Int): String
    fun committedTextLength(): Int
    fun selectedTextForInputMethod(): String?
}
