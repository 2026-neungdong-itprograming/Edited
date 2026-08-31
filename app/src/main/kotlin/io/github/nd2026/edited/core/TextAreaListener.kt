package io.github.nd2026.edited.core

/**
 * Extension point for external systems (rendering backend, Lucene/Nori lexer, JGit
 * versioning, linter, event graph, ...) to observe a [TextArea] without it knowing about
 * any of them. Implementers override only the callbacks they care about.
 */
interface TextAreaListener {
    fun onTextChanged(edit: TextEdit) {}
    fun onCaretMoved(offset: Int) {}
    fun onSelectionChanged(range: IntRange?) {}
}
