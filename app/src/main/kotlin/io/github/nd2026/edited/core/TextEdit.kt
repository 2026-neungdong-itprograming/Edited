package io.github.nd2026.edited.core

/**
 * Describes a single mutation applied to a [TextArea]. Handed to [TextAreaListener]s so
 * independent subsystems (morpheme lexer, JGit auto-commit, linter, event graph) can react
 * incrementally instead of re-scanning the whole document on every keystroke.
 */
sealed interface TextEdit {
    data class Insert(val offset: Int, val text: String) : TextEdit
    data class Delete(val start: Int, val end: Int, val removedText: String) : TextEdit
}
