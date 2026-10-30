package io.github.nd2026.edited.spell

import io.github.nd2026.edited.core.Severity
import io.github.nd2026.edited.export.ChapterText
import io.github.nd2026.edited.stats.ManuscriptStats
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpellCheckerTest {
    private fun fixes(line: String) = SpellChecker.check(line).map { it.wrong to it.suggestion }

    @Test
    fun findsCommonMistakesWithTheirWord() {
        assertEquals(listOf("됬다" to "됐다"), fixes("일이 다 잘 됬다."))
        assertEquals(listOf("몇일" to "며칠"), fixes("몇일 동안 기다렸다."))
        assertEquals(listOf("할께요" to "할게요", "할께" to "할게"), fixes("할께요, 내가 할께."))
        assertEquals(listOf("왠일이야" to "웬일이야"), fixes("왠일이야"))
        assertEquals(listOf("않" to "안"), fixes("밥을 않 먹었다."))
        assertEquals(listOf("안았다" to "않았다"), fixes("먹지 안았다."))
        val m = SpellChecker.check("그는 깨끗히 치웠다.").single()
        assertEquals("깨끗히", "그는 깨끗히 치웠다.".substring(m.start, m.end))
    }

    @Test
    fun correctProseAndNamesPass() {
        for (line in listOf(
            "서연은 창문을 열었다. 바람이 차가웠다.", "왠지 기분이 이상했다.", "웬만하면 오늘 끝내자.",
            "\"어디 가?\" 민준이 물었다.", "선생님께 인사했다.", "문을 열려고 손을 뻗었다.", "아이를 안았다.",
            "그는 아무것도 먹지 않았다.", "며칠 뒤에 만나자.",
        )) assertEquals(emptyList(), fixes(line), line)
    }

    @Test
    fun directivesAreNotChecked() {
        assertEquals(emptyList(), fixes("@메모(\"됬다는 일부러\") 좋은 날이었다."))
    }

    @Test
    fun proofreaderHonoursSuppress() {
        val text = listOf(
            "그날 일은 다 됬다.",
            "@Suppress(\"맞춤법\")",
            "사투리로 몇일이라 했다.",
            "\"그래도 됬다.\" @무시 \"정말 됬다.\"",
        ).joinToString("\n")
        val spelling = Proofreader.run(text).filter { it.message.startsWith("맞춤법") }
        assertEquals(listOf(0, 3), spelling.map { it.line })
        assertTrue(spelling.all { it.severity == Severity.ERROR })
        assertTrue(Proofreader.run("@file:Suppress(\"맞춤법\")\n됬다.").none { it.message.startsWith("맞춤법") })
    }

    @Test
    fun directivesDoNotCountOrExport() {
        val text = "@file:Suppress(\"맞춤법\")\n가나 @메모(\"나중에 고치기\") 다라.\n"
        val stats = ManuscriptStats.of(Path.of("01. 시작.episode"), text)
        assertEquals("가나 다라.".length, stats.chars)
        assertEquals(5, stats.charsNoSpace)
        assertEquals("가나 다라.", ChapterText.plain(text))
    }
}
