package io.github.nd2026.edited.search

import io.github.nd2026.edited.project.NewProjectRequest
import io.github.nd2026.edited.project.Project
import io.github.nd2026.edited.project.ProjectService
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 작업 4-5 (lemma search) and 4-7 (replace checks the next morpheme). */
class LemmaSearchTest {
    private fun project(vararg chapters: String): Project {
        val p = ProjectService.create(NewProjectRequest("검색", Files.createTempDirectory("texted-lemma").toString(), initGit = false)).project
        Files.delete(p.manuscriptDir.resolve(ProjectService.FIRST_CHAPTER))
        chapters.forEachIndexed { i, text -> Files.writeString(p.manuscriptDir.resolve("%02d. 제%d화.episode".format(i + 1, i + 1)), text) }
        return p
    }

    private fun List<SearchHit>.positions() = map { it.path.fileName.toString().take(2) + ":" + it.line + ":" + it.column }

    // --- 4-5

    @Test
    fun everyFormOfTheWordIsFound() {
        val p = project("밥을 먹었다.\n빵을 먹고 갔다.\n물을 먹는다.\n국도 먹을 것이다.")
        // 먹다 → 먹었다, 먹고, 먹는다, 먹을; the hit is where the stem starts.
        assertEquals(listOf("01:0:3", "01:1:3", "01:2:3", "01:3:3"), LemmaSearch.find(p, "먹다").positions())
        assertTrue(ProjectSearch.find(p, "먹다").isEmpty())
    }

    @Test
    fun severalFormsOnOneLineAndAcrossChapters() {
        val p = project("먹고 먹고 먹었다.", "아무것도 안 했다.", "그는 먹는다.")
        assertEquals(listOf("01:0:0", "01:0:3", "01:0:6", "03:0:3"), LemmaSearch.find(p, "먹다").positions())
    }

    @Test
    fun nounsAreMatchedAsTheyAre() {
        val p = project("문을 열었다.\n문이 닫혔다.\n창문 밖은 어두웠다.")
        assertEquals(listOf("01:0:0", "01:1:0"), LemmaSearch.find(p, "문").positions())
    }

    @Test
    fun aConjugatedQueryIsLookedUpAsItsDictionaryForm() {
        // 열린다 is 열리다 (the passive), a different dictionary word, so it is not a form of 열다.
        val p = project("문을 열었다.\n창을 열고 나갔다.\n문을 열면 비가 온다.\n문이 열린다.")
        assertEquals(3, LemmaSearch.find(p, "열었다").size)
        assertEquals(LemmaSearch.find(p, "열다"), LemmaSearch.find(p, "열었다"))
        assertEquals(3, LemmaSearch.find(p, " 열다 ").size)
        assertEquals(1, LemmaSearch.find(p, "열리다").size)
    }

    @Test
    fun noMatchesAndBlankQueries() {
        val p = project("밥을 먹었다.")
        assertTrue(LemmaSearch.find(p, "없는낱말").isEmpty())
        assertTrue(LemmaSearch.find(p, "").isEmpty())
        assertTrue(LemmaSearch.find(p, "   ").isEmpty())
        assertTrue(LemmaSearch.find(project(), "먹다").isEmpty())
    }

    @Test
    fun hitsKeepLinesOfWindowsFilesAndSkipDirectives() {
        val p = project("@메모(\"먹다 고치기\")\r\n밥을 먹었다.\r\n")
        val hits = LemmaSearch.find(p, "먹다")
        assertEquals(listOf(1 to 3), hits.map { it.line to it.column })
        assertEquals("밥을 먹었다.", hits.single().preview)
    }

    // --- 4-7

    @Test
    fun onlyAParticleAfterTheNameIsFixed() {
        fun r(text: String) = ProjectReplace.replaceInText(text, "민준", "지아")
        assertEquals("지아가 웃었다.", r("민준이 웃었다."))
        assertEquals("지아가 웃었다.", r("민준이가 웃었다."))
        assertEquals("지아를 만났다.", r("민준을 만났다."))
        // 호, 아버지 are words of their own, not particles: they stay.
        assertEquals("지아호는 왔다.", r("민준호는 왔다."))
        assertEquals("지아아버지가 왔다.", r("민준아버지가 왔다."))
        // Even when what follows looks like a particle to a character-level check.
        assertEquals("지아야 이리 와.", r("민준아 이리 와."))
    }

    @Test
    fun anUnanalyzableTailFallsBackToTheCharacterRule() {
        // Kiwi may read the last "지아로" as one noun; the particle is still fixed.
        assertEquals("민준으로", ProjectReplace.replaceInText("지아로", "지아", "민준"))
        assertEquals("민준으로 갔다. 민준이 왔다", ProjectReplace.replaceInText("지아로 갔다. 지아가 왔다", "지아", "민준"))
    }

    @Test
    fun fixParticlesCanBeSwitchedOff() {
        assertEquals("지아이 웃었다.", ProjectReplace.replaceInText("민준이 웃었다.", "민준", "지아", fixParticles = false))
    }
}
