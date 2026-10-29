package io.github.nd2026.edited.export

import io.github.nd2026.edited.project.NewProjectRequest
import io.github.nd2026.edited.project.ProjectService
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExportTest {
    private fun project() = ProjectService.create(NewProjectRequest("내보내기 <테스트>", Files.createTempDirectory("texted-export").toString(), "작가", initGit = false)).project
        .also {
            Files.writeString(it.manuscriptDir.resolve("01. 제1화.md"), "# 제1화\n\n**그날** 비가 왔다.  \n\n\n\n> 회상\n")
            Files.writeString(it.manuscriptDir.resolve("02. 제2화.md"), "# 제2화\n둘째 & 끝")
        }

    @Test
    fun plainTextRemovesMarkupAndExtraBlankLines() {
        assertEquals("제1화\n\n그날 비가 왔다.\n\n회상", ChapterText.plain("# 제1화\n\n**그날** 비가 왔다.  \n\n\n\n> 회상\n"))
        assertEquals("기울임 링크 코드 취소", ChapterText.plain("*기울임* [링크](http://a) `코드` ~~취소~~"))
        assertEquals("2 * 3 = 6", ChapterText.plain("2 * 3 = 6"))
    }

    @Test
    fun txtJoinsChaptersWithTwoBlankLines() {
        val p = project()
        val target = p.root.resolve("export/book.txt")
        TxtExporter.export(p, target)
        assertEquals("제1화\n\n그날 비가 왔다.\n\n회상\n\n\n제2화\n둘째 & 끝\n", Files.readString(target))
    }

    @Test
    fun epubHasStoredMimetypeFirstAndEscapedChapters() {
        val p = project()
        val target = p.root.resolve("export/book.epub")
        EpubExporter.export(p, target)
        ZipFile(target.toFile()).use { zip ->
            val first = zip.entries().nextElement()
            assertEquals("mimetype", first.name)
            assertEquals(ZipEntry.STORED, first.method)
            assertEquals("application/epub+zip", zip.getInputStream(first).readBytes().decodeToString())
            assertTrue(zip.getEntry("META-INF/container.xml") != null)
            val opf = zip.getInputStream(zip.getEntry("OEBPS/content.opf")).readBytes().decodeToString()
            assertTrue("내보내기 &lt;테스트&gt;" in opf)
            assertTrue("chapter2.xhtml" in opf)
            val page = zip.getInputStream(zip.getEntry("OEBPS/chapter2.xhtml")).readBytes().decodeToString()
            assertTrue("<h1>제2화</h1>" in page)
            assertTrue("<p>둘째 &amp; 끝</p>" in page)
        }
    }
}
