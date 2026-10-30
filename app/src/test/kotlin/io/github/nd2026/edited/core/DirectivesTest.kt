package io.github.nd2026.edited.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DirectivesTest {
    @Test
    fun parsesNameArgumentsAndFileLevel() {
        val line = "그는 @Suppress(\"맞춤법\", \"긴 문장\") 됬다고 했다. @file:Suppress @메모"
        val (sentence, file, note) = Directives.inLine(line)
        assertEquals("Suppress", sentence.name)
        assertEquals(listOf("맞춤법", "긴 문장"), sentence.args)
        assertEquals("@Suppress(\"맞춤법\", \"긴 문장\")", line.substring(sentence.start, sentence.end))
        assertTrue(file.fileLevel && file.isSuppress && file.args.isEmpty())
        assertEquals("메모", note.name)
        assertTrue(Directives.inLine("메일은 a@b.com 으로").isEmpty())
    }

    @Test
    fun stripRemovesDirectivesAndDirectiveOnlyLines() {
        val text = "@file:Suppress(\"맞춤법\")\n그는 @Suppress 됬다고 했다.\n@무시(\"긴 문장\")\n다음 줄."
        assertEquals("그는 됬다고 했다.\n다음 줄.", Directives.strip(text))
        assertEquals(listOf("", "그는 됬다고 했다.", "", "다음 줄."), Directives.stripLines(text.split('\n')))
        assertTrue(Directives.isDirectiveOnly("  @Suppress(\"x\")  @메모 "))
        assertFalse(Directives.isDirectiveOnly("@Suppress 본문"))
    }

    @Test
    fun scopeFollowsPlacement() {
        val lines = listOf(
            "@file:Suppress(\"습관어\")",
            "첫 문장. @Suppress(\"맞춤법\") 둘째 문장이다. 셋째 문장.",
            "@Suppress(\"긴 문장\")",
            "긴 문장이 있는 줄.",
            "아무것도 없는 줄.",
        )
        val scope = SuppressionScope.of(lines)
        // File header: every line.
        assertTrue(scope.isSuppressed("habit-word", 4))
        // Sentence: only the sentence after the directive.
        val line = lines[1]
        assertTrue(scope.isSuppressed("spelling", 1, line.indexOf("둘째")))
        assertFalse(scope.isSuppressed("spelling", 1, line.indexOf("셋째")))
        assertFalse(scope.isSuppressed("spelling", 1, 0))
        // Own line: the next line, by Korean name or id.
        assertTrue(scope.isSuppressed("long-sentence", 3))
        assertFalse(scope.isSuppressed("long-sentence", 4))
        assertFalse(scope.isSuppressed("spelling", 3))
        assertTrue(Directives.checkMatches(emptyList(), "spelling"))
        assertTrue(Directives.checkMatches(listOf("spelling"), "spelling"))
        assertTrue(Directives.checkMatches(listOf("전부"), "quote-pair"))
    }

    @Test
    fun fileDirectivesOnlyCountInTheHeader() {
        val scope = SuppressionScope.of(listOf("본문이 먼저 나온다.", "@file:Suppress"))
        assertFalse(scope.isSuppressed("spelling", 0))
    }
}
