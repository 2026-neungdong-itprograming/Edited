package io.github.nd2026.edited.vcs

import io.github.nd2026.edited.core.Sentences

data class SentenceChange(val kind: Kind, val before: String?, val after: String?) {
    enum class Kind { ADDED, REMOVED, CHANGED }
}

/**
 * Compares two versions of a chapter sentence by sentence (not line by line, which is useless for
 * prose where one paragraph is one long line). A removed sentence directly replaced by an added
 * one is reported as one CHANGED pair.
 */
object SentenceDiff {
    /** Above this many cells the LCS table is skipped and the differing middle is reported as a block. */
    private const val MAX_CELLS = 4_000_000L

    fun diff(before: String, after: String): List<SentenceChange> = diff(Sentences.split(before), Sentences.split(after))

    fun diff(a: List<String>, b: List<String>): List<SentenceChange> {
        var start = 0
        while (start < a.size && start < b.size && a[start] == b[start]) start++
        var endA = a.size
        var endB = b.size
        while (endA > start && endB > start && a[endA - 1] == b[endB - 1]) { endA--; endB-- }
        val midA = a.subList(start, endA)
        val midB = b.subList(start, endB)
        if (midA.isEmpty() && midB.isEmpty()) return emptyList()
        if (midA.size.toLong() * midB.size > MAX_CELLS) return block(midA, midB)

        // lcs[i][j] = LCS length of midA[i..] and midB[j..].
        val lcs = Array(midA.size + 1) { IntArray(midB.size + 1) }
        for (i in midA.indices.reversed()) for (j in midB.indices.reversed()) {
            lcs[i][j] = if (midA[i] == midB[j]) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
        }
        val result = ArrayList<SentenceChange>()
        val removed = ArrayList<String>()
        val added = ArrayList<String>()
        fun flush() { result += block(removed, added); removed.clear(); added.clear() }
        var i = 0
        var j = 0
        while (i < midA.size || j < midB.size) {
            when {
                i < midA.size && j < midB.size && midA[i] == midB[j] -> { flush(); i++; j++ }
                j < midB.size && (i == midA.size || lcs[i][j + 1] >= lcs[i + 1][j]) -> added += midB[j++]
                else -> removed += midA[i++]
            }
        }
        flush()
        return result
    }

    private fun block(removed: List<String>, added: List<String>): List<SentenceChange> {
        val paired = minOf(removed.size, added.size)
        return List(paired) { SentenceChange(SentenceChange.Kind.CHANGED, removed[it], added[it]) } +
            removed.drop(paired).map { SentenceChange(SentenceChange.Kind.REMOVED, it, null) } +
            added.drop(paired).map { SentenceChange(SentenceChange.Kind.ADDED, null, it) }
    }
}
