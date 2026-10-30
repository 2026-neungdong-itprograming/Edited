package io.github.nd2026.edited.spell

import io.github.nd2026.edited.core.Directives

/** A misspelled span of one line: columns [start]..[end) hold [wrong], which should read [suggestion]. */
data class Misspelling(val start: Int, val end: Int, val wrong: String, val suggestion: String) {
    val message: String get() = "맞춤법: '$wrong' → '$suggestion'"
}

/**
 * Finds commonly misspelled Korean words (국립국어원 "자주 틀리는 맞춤법" 위주). Rule-based on
 * purpose: Kiwi's typo model silently normalizes `됬` to `되+었` and knows `금새`, `역활` as words,
 * so it cannot tell a typo from the author's intent. Every rule here is a spelling that is wrong
 * wherever it appears (or with the word-start/ending guard it declares), so a hit is never a
 * character name or a stylistic choice. Directives (`@Suppress(...)`) are skipped.
 */
object SpellChecker {
    /** Check id for `@Suppress("맞춤법")` / `@Suppress("spelling")`. */
    const val CHECK_ID = "spelling"

    private class Rule(val pattern: Regex, val fix: (String) -> String)

    /** Not preceded by a letter: the match starts a word. */
    private const val WORD_START = """(?<![\p{L}\p{N}])"""

    /** Hangul syllables with final ㄹ (할, 갈, 볼 …), for the "-ㄹ께" rule. */
    private val RIEUL_FINAL: String = buildString {
        for (syllable in 0 until 11172) if (syllable % 28 == 8) append((0xAC00 + syllable).toChar())
    }

    private fun word(wrong: String, right: String) = Rule(Regex(Regex.escape(wrong))) { right }
    private fun wordStart(wrong: String, right: String) = Rule(Regex(WORD_START + Regex.escape(wrong))) { right }

    private val rules: List<Rule> = listOf(
        word("됬", "됐"),
        word("몇일", "며칠"),
        word("어떻해", "어떡해"),
        word("금새", "금세"),
        word("웬지", "왠지"),
        // 왠 is only right in 왠지 (왜인지); 왠일·왠만하면 are 웬.
        Rule(Regex("왠(?!지)")) { "웬" },
        word("깨끗히", "깨끗이"), word("일일히", "일일이"), word("곰곰히", "곰곰이"), word("틈틈히", "틈틈이"),
        word("번번히", "번번이"), word("깊숙히", "깊숙이"), word("따뜻히", "따뜻이"), word("솔직이", "솔직히"),
        word("꼼꼼이", "꼼꼼히"), word("가만이", "가만히"),
        word("역활", "역할"), word("희안", "희한"), word("설겆이", "설거지"), word("오랫만", "오랜만"),
        word("어의없", "어이없"), word("무릎쓰", "무릅쓰"), word("짜집기", "짜깁기"),
        word("뵈요", "봬요"), word("되요", "돼요"), word("되서", "돼서"),
        word("있슴", "있음"), word("없슴", "없음"), word("아니예요", "아니에요"), word("이예요", "이에요"),
        word("어쨋든", "어쨌든"), word("새벽녁", "새벽녘"), word("눈쌀", "눈살"), word("닥달", "닦달"),
        word("설레임", "설렘"), word("내노라", "내로라"), word("뒤치닥거리", "뒤치다꺼리"), word("곱배기", "곱빼기"),
        word("육계장", "육개장"), word("어물쩡", "어물쩍"), word("희노애락", "희로애락"), word("폭팔", "폭발"),
        word("쑥쓰럽", "쑥스럽"), word("어줍잖", "어쭙잖"), word("건들이", "건드리"), word("가르키", "가리키"),
        word("제작년", "재작년"), word("돌맹이", "돌멩이"), word("통채", "통째"), word("널부러", "널브러"),
        word("삼가하", "삼가"), word("치뤘", "치렀"), word("치뤄", "치러"), word("치루", "치르"),
        word("잠궜", "잠갔"), word("잠궈", "잠가"), word("담궜", "담갔"), word("담궈", "담가"),
        word("개거품", "게거품"), word("사겼", "사귀었"), word("괜찬", "괜찮"),
        word("할려고", "하려고"), word("갈려고", "가려고"), word("볼려고", "보려고"), word("줄려고", "주려고"),
        word("올려고", "오려고"), word("을려고", "으려고"),
        wordStart("구지", "굳이"),
        // 할께 → 할게, 갈께요 → 갈게요: -ㄹ게 is never spelled with ㄲ.
        Rule(Regex("(?<=[$RIEUL_FINAL])께(?=요?(?![\\p{L}]))")) { "게" },
        // "않 했다" → "안 했다": 않 never starts a word.
        Rule(Regex("$WORD_START(않)(?=\\s)")) { "안" },
        // "먹지 안았다" → "먹지 않았다".
        Rule(Regex("(?<=지\\s?)안(?=았|는다|는|고|을|은)")) { "않" },
    )

    /** Misspellings in one line, by column, outside any directive. */
    fun check(line: String): List<Misspelling> {
        if (line.isBlank()) return emptyList()
        val directives = Directives.inLine(line).map { it.start until it.end }
        val found = ArrayList<Misspelling>()
        for (rule in rules) {
            for (m in rule.pattern.findAll(line)) {
                if (directives.any { m.range.first in it }) continue
                // Show the whole word around the mistake so the author sees it in context: "됬다" → "됐다".
                val wordStart = (m.range.first downTo 0).firstOrNull { it == 0 || !line[it - 1].isLetterOrDigit() } ?: 0
                val wordEnd = (m.range.last + 1..line.length).first { it == line.length || !line[it].isLetterOrDigit() }
                val fixed = rule.fix(m.value)
                found += Misspelling(
                    m.range.first, m.range.last + 1,
                    line.substring(wordStart, wordEnd),
                    line.substring(wordStart, m.range.first) + fixed + line.substring(m.range.last + 1, wordEnd),
                )
            }
        }
        return found.sortedBy { it.start }
    }
}
