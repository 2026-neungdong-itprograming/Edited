package io.github.nd2026.edited.core

/**
 * Splits manuscript text into sentences for the inspection, statistics and diff features, so they
 * all agree on what "one sentence" is.
 *
 * A sentence ends at `.`, `!`, `?` or `…` (a run such as `?!` or `...` counts once), together with
 * any closing quotes/brackets right after it (`했다."` stays one sentence), or at a line break.
 * Results are trimmed and blank ones dropped.
 */
object Sentences {
    private const val TERMINATORS = ".!?…"
    private const val CLOSERS = "\"'”’)]」』》〉"

    fun split(text: String): List<String> {
        val result = ArrayList<String>()
        val current = StringBuilder()
        fun emit() {
            val sentence = current.toString().trim()
            if (sentence.isNotEmpty()) result.add(sentence)
            current.setLength(0)
        }
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '\n' || c == '\r' -> emit()
                c in TERMINATORS -> {
                    current.append(c)
                    while (i + 1 < text.length && (text[i + 1] in TERMINATORS || text[i + 1] in CLOSERS)) current.append(text[++i])
                    emit()
                }
                else -> current.append(c)
            }
            i++
        }
        emit()
        return result
    }
}
