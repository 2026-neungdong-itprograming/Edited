package io.github.nd2026.edited.project

import java.nio.file.Path

/**
 * Texted's own document formats. All three are the same thing on disk - plain UTF-8 prose, one
 * paragraph per line, no markup at all - and differ only in extension and where they live:
 *
 * ```
 * manuscript/03. 낯선 손님.episode     서연은 창문을 열었다. 바람이 차가웠다.
 *                                      "어디 가?" 민준이 물었다.
 *                                      (빈 줄 = 문단 띄우기, "* * *" = 장면 전환 - 둘 다 그냥 글자)
 *
 * characters/한서연.character         별칭: 서연, 한 대리
 *                                      27세. 주인공의 직장 동료.
 * ```
 *
 * - The title is the file name: extension dropped, and for episodes the leading order number
 *   (`03. `) too - see [ChapterTitle]. Nothing in the file has to be skipped, so every line the
 *   linter, statistics, search or Git diff sees is the author's own sentence.
 * - Sheets (`.character`, `.world`) keep the existing card layout: an optional `별칭:` line, the
 *   rest is the description; the name is the file name.
 * - `.md` and `.txt` files keep working; these formats are only what Texted creates.
 */
enum class NovelFormat(val extension: String, val label: String, val folder: String, val template: String) {
    EPISODE("episode", "에피소드", Project.MANUSCRIPT_DIR, ""),
    CHARACTER("character", "캐릭터", Project.CHARACTERS_DIR, "별칭: \n\n"),
    WORLD("world", "세계관", Project.WORLD_DIR, "별칭: \n\n");

    val dotExtension: String get() = ".$extension"

    companion object {
        /** Older text formats that are still read everywhere (and offered by "확장자 바꾸기"). */
        val LEGACY_EXTENSIONS: List<Pair<String, String>> = listOf("txt" to "텍스트", "md" to "마크다운")

        /** Every extension Texted opens as a manuscript or sheet. */
        val TEXT_EXTENSIONS: Set<String> = entries.map { it.extension }.toSet() + LEGACY_EXTENSIONS.map { it.first }

        fun of(path: Path): NovelFormat? = ofName(path.fileName?.toString().orEmpty())

        fun ofName(name: String): NovelFormat? {
            val extension = name.substringAfterLast('.', "").lowercase()
            return entries.firstOrNull { it.extension == extension }
        }

        fun isText(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in TEXT_EXTENSIONS
    }
}
