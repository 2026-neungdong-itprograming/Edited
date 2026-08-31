package io.github.nd2026.edited.core

interface TextBuffer {
    val length: Int
    val lineCount: Int

    fun charAt(index: Int): Char
    fun subSequence(start: Int, end: Int): String

    fun lineStart(line: Int): Int
    fun lineEnd(line: Int): Int
    fun lineOf(offset: Int): Int

    fun insert(offset: Int, text: CharSequence)
    fun delete(start: Int, end: Int)
}
