package io.github.nd2026.edited.world

import io.github.nd2026.edited.project.NewProjectRequest
import io.github.nd2026.edited.project.ProjectService
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CharacterRepositoryTest {
    private fun project() = ProjectService.create(NewProjectRequest("설정", Files.createTempDirectory("texted-world").toString(), initGit = false)).project

    @Test
    fun parsesNameAliasesAndDescription() {
        val sheet = CharacterRepository.parse(Path.of("seoyeon.md"), "# 한서연\n별칭: 서연, 한 대리\n27세.\n주인공의 동료.\n")
        assertEquals("한서연", sheet.name)
        assertEquals(listOf("서연", "한 대리"), sheet.aliases)
        assertEquals("27세.\n주인공의 동료.", sheet.description)
    }

    @Test
    fun toleratesMissingParts() {
        val noHeading = CharacterRepository.parse(Path.of("민준.md"), "그냥 메모")
        assertEquals("민준", noHeading.name)
        assertTrue(noHeading.aliases.isEmpty())
        val empty = CharacterRepository.parse(Path.of("빈.md"), "")
        assertEquals("빈", empty.name)
        assertEquals("", empty.description)
    }

    @Test
    fun loadsCharactersAndWorldAndCountsAppearances() {
        val p = project()
        Files.writeString(p.charactersDir.resolve("minjun.md"), "# 김민준\n별칭: 민준")
        Files.writeString(p.worldDir.resolve("city.md"), "# 은하시\n배경 도시")
        Files.writeString(p.manuscriptDir.resolve("01. 제1화.md"), "# 제1화\n김민준이 왔다. 민준은 웃었다.")
        Files.writeString(p.manuscriptDir.resolve("02. 제2화.md"), "# 제2화\n아무도 없다.")

        val sheet = CharacterRepository.load(p).single()
        assertEquals("은하시", WorldRepository.load(p).single().name)
        val appearances = CharacterUsage.appearances(p, sheet)
        assertEquals(listOf("제1화"), appearances.map { it.title })
        assertEquals(2, appearances.single().count)
    }

    @Test
    fun longerTermsWinWhenCounting() {
        assertEquals(1, CharacterUsage.count("김민준", listOf("민준", "김민준")))
        assertEquals(0, CharacterUsage.count("아무도", listOf("")))
    }
}
