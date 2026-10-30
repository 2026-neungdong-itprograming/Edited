package io.github.nd2026.edited.inspect

import io.github.nd2026.edited.core.Inspection
import io.github.nd2026.edited.core.Severity
import io.github.nd2026.edited.lexer.Morpheme

/**
 * 1-8. Double passives such as "열려졌다" (열리/VV + 어/EC + 지/VX), "보여지다" or "결정되어졌다" → WARNING.
 * A verb that is already passive (열리다, 보이다, 되다 …) does not need `-어지다` on top. Active verbs
 * with the same shape ("만들어졌다" = 만들/VV, "기다려졌다" = 기다리/VV) are fine, so only the stems in
 * [PASSIVE_STEMS] and 되(다) count. Text that Kiwi cannot split into a verb ("되어지다" on its own) is caught by [BARE_DOUBLE].
 */
class PassiveVoiceRule : MorphemeRule {
    override val id = "double-passive"

    override fun check(lines: List<String>, morphemes: List<List<Morpheme>>): List<Inspection> {
        val result = ArrayList<Inspection>()
        lines.forEachIndexed { line, text ->
            val reported = HashSet<Int>()
            val ms = morphemes.getOrElse(line) { emptyList() }
            for (i in 0 until ms.size - 2) {
                val stem = ms[i]
                if (!isPassiveStem(stem) || !isAuxiliaryChain(ms[i + 1], ms[i + 2]) || !reported.add(stem.start)) continue
                result += report(line, text, stem.start, if (stem.surface == "되") "되다" else stem.surface + "다")
            }
            for (m in BARE_DOUBLE.findAll(text)) if (reported.add(m.range.first)) result += report(line, text, m.range.first, "되다")
        }
        return result
    }

    private fun isPassiveStem(m: Morpheme): Boolean =
        (m.partOfSpeech == "VV" && m.surface in PASSIVE_STEMS) || ((m.partOfSpeech == "VV" || m.partOfSpeech == "XSV") && m.surface == "되")

    /** 어/아 (EC) followed by the auxiliary 지 (VX): the `-어지다` that turns a verb passive. */
    private fun isAuxiliaryChain(connective: Morpheme, auxiliary: Morpheme): Boolean =
        connective.partOfSpeech == "EC" && connective.surface in CONNECTIVES && auxiliary.partOfSpeech == "VX" && auxiliary.surface == "지"

    private fun report(line: Int, text: String, column: Int, dictionaryForm: String): Inspection {
        var start = column
        while (start > 0 && !text[start - 1].isWhitespace()) start--
        var end = column
        while (end < text.length && !text[end].isWhitespace()) end++
        val word = text.substring(start, end).trim { !it.isLetterOrDigit() }
        return Inspection(line, Severity.WARNING, "이중 피동입니다: '$word' ('$dictionaryForm'는 이미 피동이라 '-어지다'를 또 붙이지 않아도 됩니다)")
    }

    private companion object {
        val CONNECTIVES = setOf("어", "아", "여")

        /** Passive verbs formed with -이-/-히-/-리-/-기-; their stems as Kiwi returns them. */
        val PASSIVE_STEMS = setOf(
            "열리", "닫히", "잡히", "먹히", "보이", "쓰이", "풀리", "걸리", "물리", "들리", "팔리", "읽히", "놓이", "쌓이", "섞이",
            "막히", "묶이", "꺾이", "찢기", "뜯기", "안기", "감기", "잠기", "쫓기", "끌리", "밟히", "박히", "얽히", "꽂히", "맺히",
            "묻히", "불리", "깎이", "빼앗기", "갈리", "베이", "패이", "파이", "덮이", "눌리", "꼬이", "트이", "뽑히",
        )

        /** "되어지다" / "되어졌다" / "되어진" / "되어질" - 되다 plus -어지다 even where Kiwi has no verb to attach it to. */
        val BARE_DOUBLE = Regex("되어(?:지|졌|진|질|져)")
    }
}
