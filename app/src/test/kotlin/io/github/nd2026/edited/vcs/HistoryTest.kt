package io.github.nd2026.edited.vcs

import io.github.nd2026.edited.project.NewProjectRequest
import io.github.nd2026.edited.project.ProjectService
import io.github.nd2026.edited.vcs.SentenceChange.Kind
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HistoryTest {
    @Test
    fun commitsLogsAndReadsOldVersions() {
        val project = ProjectService.create(NewProjectRequest("기록", Files.createTempDirectory("texted-vcs").toString(), "작가")).project
        val history = HistoryService(project.root)
        assertEquals(1, history.log().size) // the project-creation commit
        assertNull(history.commitAll("변경 없음"))

        val chapter = project.chapters().single().path
        Files.writeString(chapter, "# 제1화\n첫 문장.")
        val first = assertNotNull(history.commitAll("첫 저장"))
        assertEquals("작가", first.author)
        Files.writeString(chapter, "# 제1화\n고친 문장.")
        Files.createDirectories(project.root.resolve("export"))
        Files.writeString(project.root.resolve("export/book.txt"), "내보낸 파일")
        history.commitAll("둘째 저장")

        assertEquals(listOf("둘째 저장", "첫 저장"), history.log(2).map { it.message })
        assertEquals("# 제1화\n첫 문장.", history.fileAt(first.id, chapter))
        assertNull(history.fileAt(first.id, project.root.resolve("export/book.txt")))
        assertNull(history.commitAll("내보내기만 바뀜"))
    }

    @Test
    fun createsRepositoryOnFirstCommit() {
        val project = ProjectService.create(NewProjectRequest("Git 없음", Files.createTempDirectory("texted-vcs").toString(), initGit = false)).project
        val history = HistoryService(project.root)
        assertTrue(history.log().isEmpty())
        assertNotNull(history.commitAll("처음"))
        assertEquals(1, history.log().size)
    }

    @Test
    fun sentenceDiff() {
        val changes = SentenceDiff.diff("문을 열었다. 방은 어두웠다. 그는 웃었다.", "문을 열었다. 방은 밝았다. 그는 웃었다. 끝.")
        assertEquals(
            listOf(SentenceChange(Kind.CHANGED, "방은 어두웠다.", "방은 밝았다."), SentenceChange(Kind.ADDED, null, "끝.")),
            changes,
        )
        assertEquals(listOf(Kind.REMOVED), SentenceDiff.diff("하나. 둘.", "하나.").map { it.kind })
        assertTrue(SentenceDiff.diff("같다.", "같다.").isEmpty())
    }
}
