package io.github.nd2026.edited.search

import io.github.nd2026.edited.project.NewProjectRequest
import io.github.nd2026.edited.project.ProjectService
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchAndReplaceTest {
    private fun project() = ProjectService.create(NewProjectRequest("검색", Files.createTempDirectory("texted-search").toString(), initGit = false)).project

    @Test
    fun findsEveryOccurrenceWithPosition() {
        val hits = ProjectSearch.findIn(Path.of("a.md"), "첫 줄\n민준과 민준이\n", "민준")
        assertEquals(listOf(1 to 0, 1 to 4), hits.map { it.line to it.column })
        assertTrue(ProjectSearch.findIn(Path.of("a.md"), "없음", "민준").isEmpty())
        assertTrue(ProjectSearch.findIn(Path.of("a.md"), "아무거나", "").isEmpty())
    }

    @Test
    fun particlesFollowTheNewName() {
        fun r(text: String, from: String, to: String) = ProjectReplace.replaceInText(text, from, to)
        // 받침 있음 → 없음
        assertEquals("지아가 웃었다. 지아가 왔다.", r("민준이 웃었다. 민준이가 왔다.", "민준", "지아"))
        assertEquals("지아를 보고 지아는 지아와 지아야!", r("민준을 보고 민준은 민준과 민준아!", "민준", "지아"))
        assertEquals("지아였다. 지아예요. 지아로", r("민준이었다. 민준이에요. 민준으로", "민준", "지아"))
        // 받침 없음 → 있음
        assertEquals("민준이 웃었다. 민준을 민준은 민준과 민준으로", r("지아가 웃었다. 지아를 지아는 지아와 지아로", "지아", "민준"))
        // ㄹ 받침은 '로'
        assertEquals("하늘로 하늘이", r("지아로 지아가", "지아", "하늘"))
        // 받침이 같으면 그대로, 조사가 아닌 말도 그대로
        assertEquals("서연이가 서연의", r("민준이가 민준의", "민준", "서연"))
        assertEquals("지아에게", r("민준에게", "민준", "지아"))
        assertEquals("Alex가", r("민준가", "민준", "Alex"))
    }

    @Test
    fun previewThenApplyRewritesFiles() {
        val p = project()
        val chapter = p.manuscriptDir.resolve("01. 제1화.md")
        Files.writeString(chapter, "# 제1화\n민준이 왔다.\n아무도 없다.\n")
        val preview = ProjectReplace.preview(p, "민준", "지아")
        assertEquals(listOf("지아가 왔다."), preview.map { it.after })
        assertEquals(1, ProjectReplace.apply(p, "민준", "지아"))
        assertEquals("# 제1화\n지아가 왔다.\n아무도 없다.\n", Files.readString(chapter))
        assertEquals(0, ProjectReplace.apply(p, "민준", "지아"))
        assertEquals(1, ProjectSearch.find(p, "지아").size)
    }
}
