package io.github.nd2026.edited.export

import io.github.nd2026.edited.project.NewProjectRequest
import io.github.nd2026.edited.project.Project
import io.github.nd2026.edited.project.ProjectService
import java.nio.file.Files
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 작업 5-5, 5-6, 5-7. */
class ChapterTagsTest {
    private val text = "서연은 마법학교에 갔다. 서연은 마법학교의 종소리를 들었다. 마법학교 앞에서 서연은 웃었다. 그것은 오랜 꿈이었다."

    private fun project(vararg chapters: String): Project {
        val p = ProjectService.create(NewProjectRequest("태그 <책>", Files.createTempDirectory("texted-tags").toString(), "작가", initGit = false)).project
        Files.delete(p.manuscriptDir.resolve(ProjectService.FIRST_CHAPTER))
        chapters.forEachIndexed { i, body -> Files.writeString(p.manuscriptDir.resolve("%02d. 제%d화.episode".format(i + 1, i + 1)), body) }
        return p
    }

    // --- 5-5

    @Test
    fun namesComeFirstThenCommonNouns() {
        val tags = ChapterTags.of(text)
        assertEquals("서연", tags.first())
        assertTrue("학교" in tags && "마법" in tags, "$tags")
        assertTrue(tags.indexOf("서연") < tags.indexOf("학교"))
        assertEquals(2, ChapterTags.of(text, limit = 2).size)
        assertEquals(listOf("서연"), ChapterTags.of(text, limit = 1))
    }

    @Test
    fun oneLetterAndEverydayWordsAreNeverTags() {
        val tags = ChapterTags.of("그는 문을 열고 것을 봤다. 때가 되어 수를 셌다. 말을 했다. 순간 정도 사이 하늘을 봤다.", limit = 20)
        for (stop in listOf("것", "수", "때", "말", "문", "순간", "정도", "사이")) assertTrue(stop !in tags, "$stop in $tags")
        assertTrue("하늘" in tags)
    }

    @Test
    fun emptyAndMarkupFreeText() {
        assertTrue(ChapterTags.of("").isEmpty())
        assertTrue(ChapterTags.of("  \n\n").isEmpty())
        assertTrue(ChapterTags.of(text, limit = 0).isEmpty())
        // Directives and Markdown headings are not tags.
        val tags = ChapterTags.of("# 제1화\n@Suppress(\"맞춤법\")\n마법학교의 종이 울렸다. 마법학교는 조용했다.", limit = 10)
        assertTrue(tags.none { "Suppress" in it || "맞춤법" in it })
        assertTrue("학교" in tags)
    }

    // --- 5-6

    @Test
    fun theWholeBookIsRankedAcrossChapters() {
        val p = project("서연은 웃었다.", "서연이 왔다. 마법학교는 조용했다.", "마법학교로 갔다. 서연은 울었다.")
        val tags = ChapterTags.ofProject(p)
        assertEquals("서연", tags.first())
        assertTrue("학교" in tags)
        assertEquals(1, ChapterTags.ofProject(p, limit = 1).size)
        assertTrue(ChapterTags.ofProject(project()).isEmpty())
    }

    @Test
    fun epubListsTheTagsAsSubjects() {
        val p = project("서연은 마법학교에 갔다. 서연은 웃었다.", "서연이 왔다. 마법학교는 조용했다.")
        val target = p.root.resolve("export/book.epub")
        EpubExporter.export(p, target)
        ZipFile(target.toFile()).use { zip ->
            val opf = zip.getInputStream(zip.getEntry("OEBPS/content.opf")).readBytes().decodeToString()
            assertTrue("<dc:subject>서연</dc:subject>" in opf, opf)
            assertTrue("<dc:subject>학교</dc:subject>" in opf, opf)
            assertTrue("태그 &lt;책&gt;" in opf)
            // Subjects sit in the metadata block, before the manifest.
            assertTrue(opf.indexOf("<dc:subject>") < opf.indexOf("<manifest>"))
        }
    }

    @Test
    fun epubWithoutTagsStillValid() {
        val p = project("")
        val target = p.root.resolve("export/empty.epub")
        EpubExporter.export(p, target)
        ZipFile(target.toFile()).use { zip ->
            val opf = zip.getInputStream(zip.getEntry("OEBPS/content.opf")).readBytes().decodeToString()
            assertTrue("<dc:subject>" !in opf)
            assertTrue("<dc:language>" in opf && "<meta property=\"dcterms:modified\">" in opf)
        }
    }

    // --- 5-7

    @Test
    fun hashtagLine() {
        assertEquals("#서연", ChapterText.hashtags(text, limit = 1))
        val line = ChapterText.hashtags(text)
        assertTrue(line.startsWith("#서연 #"), line)
        assertTrue(line.split(' ').all { it.startsWith("#") && it.length > 1 })
        assertEquals("", ChapterText.hashtags(""))
        // The existing plain-text conversion is untouched.
        assertEquals("제1화\n\n그날 비가 왔다.", ChapterText.plain("# 제1화\n\n**그날** 비가 왔다."))
    }
}
