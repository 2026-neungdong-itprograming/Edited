package io.github.nd2026.edited.ai

/** Writing help for a novelist, on top of any [LlmClient]. Blocking; call off the EDT. */
class ProseAssistant(private val client: LlmClient) {

    /** Rewrites [passage] to read better while keeping its meaning, voice and line breaks. */
    fun polish(passage: String): String = clean(
        client.chat(
            listOf(
                LlmClient.Message("system", POLISH_PROMPT),
                LlmClient.Message("user", passage),
            ),
        ),
        passage,
    )

    companion object {
        private val POLISH_PROMPT = """
            당신은 한국어 웹소설 교정 편집자입니다. 사용자가 보낸 원고 조각을 더 읽기 좋게 다듬어 주세요.
            - 내용, 시점, 시제, 말투, 인물 이름은 바꾸지 않습니다.
            - 어색한 문장, 불필요한 반복, 이중 피동, 맞춤법과 띄어쓰기를 고칩니다.
            - 줄바꿈(문단) 개수와 대사의 따옴표는 그대로 둡니다.
            - 설명 없이 다듬은 원고만 돌려줍니다.
        """.trimIndent()

        /** Strips a code fence the model may wrap around the answer, and keeps the passage's trailing newline. */
        internal fun clean(answer: String, passage: String): String {
            var text = answer.trim()
            if (text.startsWith("```")) text = text.substringAfter('\n').substringBeforeLast("```").trim()
            return if (passage.endsWith("\n")) text + "\n" else text
        }
    }
}
