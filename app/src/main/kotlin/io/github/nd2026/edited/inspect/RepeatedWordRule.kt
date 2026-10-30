package io.github.nd2026.edited.inspect

import io.github.nd2026.edited.core.Inspection
import io.github.nd2026.edited.core.Severity
import io.github.nd2026.edited.lexer.CommonWords
import io.github.nd2026.edited.lexer.Morpheme
import io.github.nd2026.edited.lexer.isContentWord
import io.github.nd2026.edited.lexer.isProperNoun
import io.github.nd2026.edited.lexer.lemma

/**
 * 1-6. The same content word (by lemma) used again within [WINDOW] sentences of one paragraph → INFO.
 * "그는 문을 열었다. 문 밖은 추웠다." reports `문`. A paragraph is the run of non-blank lines, so a blank line
 * starts over. Particles and endings never count; neither do proper nouns (a name naturally repeats) or the
 * everyday light words in [CommonWords]. Reported once per word per line, on the line holding the repeat.
 */
class RepeatedWordRule : MorphemeRule {
    override val id = "repeated-word"

    override fun check(lines: List<String>, morphemes: List<List<Morpheme>>): List<Inspection> {
        val result = ArrayList<Inspection>()
        var lastSeen = HashMap<String, Int>()
        var sentence = 0
        lines.forEachIndexed { line, text ->
            if (text.isBlank()) {
                lastSeen = HashMap()
                sentence = 0
                return@forEachIndexed
            }
            val reported = HashSet<String>()
            for (s in MorphemeSentences.split(morphemes.getOrElse(line) { emptyList() })) {
                for (m in s) {
                    if (!counts(m)) continue
                    val lemma = m.lemma
                    val previous = lastSeen[lemma]
                    if (previous != null && sentence - previous < WINDOW && reported.add(lemma)) {
                        result += Inspection(line, Severity.INFO, "'$lemma'이(가) ${WINDOW}문장 안에 다시 나옵니다")
                    }
                    lastSeen[lemma] = sentence
                }
                sentence++
            }
        }
        return result
    }

    private fun counts(m: Morpheme): Boolean =
        m.isContentWord && !m.isProperNoun && m.lemma !in CommonWords.LIGHT_PREDICATES &&
            m.lemma !in CommonWords.COMMON_ADVERBS && m.lemma !in CommonWords.COMMON_NOUNS

    private companion object {
        /** A repeat counts when it is at most this many sentences after the first use, that sentence included. */
        const val WINDOW = 3
    }
}
