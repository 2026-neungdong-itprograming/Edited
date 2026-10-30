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
            You are a professional Korean web novel supervisor and editor. Polish the manuscript excerpts provided by the user to improve readability.
            - Preserve the original content, narrative perspective, tense, tone, and character names.
            - Correct awkward phrasing, unnecessary repetition, double passive constructions, spelling, grammar, and spacing.
            - Preserve the original number of line breaks (paragraphs) and keep all dialogue quotation marks unchanged.
            - Return only the polished manuscript, without any explanations or additional commentary.
        """.trimIndent()

        /** Strips a code fence the model may wrap around the answer, and keeps the passage's trailing newline. */
        internal fun clean(answer: String, passage: String): String {
            var text = answer.trim()
            if (text.startsWith("```")) text = text.substringAfter('\n').substringBeforeLast("```").trim()
            return if (passage.endsWith("\n")) text + "\n" else text
        }
    }
}
