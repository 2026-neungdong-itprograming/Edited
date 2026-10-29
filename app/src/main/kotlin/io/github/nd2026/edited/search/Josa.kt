package io.github.nd2026.edited.search

/**
 * Korean particle (조사) agreement for renaming: replacing `민준` with `지아` should turn
 * "민준이가 웃었다" into "지아가 웃었다" and "민준을" into "지아를".
 *
 * Heuristic, by design: it only looks at the characters right after the replaced word, which is
 * where a particle attaches. The replace preview lets the author check the result.
 */
object Josa {
    /** (form after a final consonant, form after a vowel), longest first so `이에요` wins over `이`. */
    private val PAIRS = listOf(
        "이라고" to "라고", "이라는" to "라는", "이에요" to "예요", "이었" to "였",
        "이랑" to "랑", "이나" to "나", "이며" to "며", "이야" to "야", "이여" to "여",
        "과" to "와", "을" to "를", "은" to "는", "아" to "야",
    )

    /** True if [c] is a Hangul syllable with a final consonant (받침). */
    fun hasBatchim(c: Char): Boolean = c in '가'..'힣' && (c - '가') % 28 != 0

    /** `으로` is used after a final consonant other than ㄹ; `로` otherwise. */
    private fun takesEuro(c: Char): Boolean = hasBatchim(c) && (c - '가') % 28 != 8

    /**
     * Given the text [rest] that follows a replaced [from], returns how many of its characters to
     * drop and what to put in their place so the particle agrees with [to]. (0, "") means no change.
     */
    fun adjust(from: String, to: String, rest: String): Pair<Int, String> {
        val a = from.lastOrNull() ?: return 0 to ""
        val b = to.lastOrNull() ?: return 0 to ""
        if (a !in '가'..'힣' || b !in '가'..'힣') return 0 to ""
        val fromB = hasBatchim(a)
        val toB = hasBatchim(b)

        // 으로/로 depends on the ㄹ final as well, so check it before the plain batchim pairs.
        if (rest.startsWith("으로") && takesEuro(a) && !takesEuro(b)) return 2 to "로"
        if (rest.startsWith("로") && !takesEuro(a) && takesEuro(b)) return 1 to "으로"
        if (fromB == toB) return 0 to ""

        for ((consonant, vowel) in PAIRS) {
            if (fromB && rest.startsWith(consonant)) return consonant.length to vowel
            if (!fromB && rest.startsWith(vowel)) return vowel.length to consonant
        }
        if (fromB && rest.startsWith("이")) {
            // "민준이가": the 이 is the name suffix and the next particle already follows a vowel - drop 이.
            // "민준이 웃었다": 이 is the subject particle itself - it becomes 가.
            val next = rest.getOrNull(1)
            return if (next != null && next in '가'..'힣') 1 to "" else 1 to "가"
        }
        if (!fromB && rest.startsWith("가")) return 1 to "이"
        return 0 to ""
    }
}
