package io.github.nd2026.edited.lexer

/**
 * Words so common that counting them says nothing about a chapter's style: repetition checks, top-word
 * lists and tags skip them. Lead-owned; tasks only read it. Entries are dictionary forms ([lemma]).
 */
object CommonWords {
    /** Verbs and adjectives that carry little meaning of their own (하다, 있다, 되다 …). */
    val LIGHT_PREDICATES: Set<String> = setOf(
        "하다", "있다", "없다", "되다", "이다", "같다", "보다", "오다", "가다", "나다", "주다", "받다", "두다", "놓다", "알다",
        "않다", "못하다", "아니다", "그렇다", "이렇다", "어떻다", "그러다", "이러다",
    )

    /** Adverbs that connect or intensify rather than describe. */
    val COMMON_ADVERBS: Set<String> = setOf(
        "이미", "다시", "또", "더", "잘", "안", "못", "곧", "다", "좀", "그냥", "아직", "가장", "모두", "정말", "너무", "매우", "아주", "다시금",
    )

    /** Nouns that are never a good tag or a meaningful repeat (것, 수, 때 … and their two-syllable cousins). */
    val COMMON_NOUNS: Set<String> = setOf(
        "것", "수", "때", "말", "일", "중", "점", "번", "등", "곳", "데", "뒤", "앞", "안", "밖", "위", "아래", "분", "쪽", "채",
        "순간", "때문", "정도", "사이", "자신", "이후", "이전", "하나", "다음", "동안", "사실", "경우", "이상", "이하", "지금",
        "오늘", "어제", "내일", "여기", "거기", "저기", "누구", "무엇", "아무", "처음", "마지막", "나머지", "모습", "생각",
    )
}
