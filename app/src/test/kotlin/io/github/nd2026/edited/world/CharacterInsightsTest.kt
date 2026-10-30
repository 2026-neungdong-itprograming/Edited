package io.github.nd2026.edited.world

import io.github.nd2026.edited.lexer.DefaultMorphemeAnalyzer
import io.github.nd2026.edited.lexer.Morphology
import io.github.nd2026.edited.project.NewProjectRequest
import io.github.nd2026.edited.project.Project
import io.github.nd2026.edited.project.ProjectService
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 작업 3-5 (suggestions) and 3-7 (morpheme-based counting). */
class CharacterInsightsTest {
    private fun project(vararg chapters: String): Project {
        val p = ProjectService.create(NewProjectRequest("추천", Files.createTempDirectory("texted-suggest").toString(), initGit = false)).project
        Files.delete(p.manuscriptDir.resolve(ProjectService.FIRST_CHAPTER))
        chapters.forEachIndexed { i, text -> Files.writeString(p.manuscriptDir.resolve("%02d. 제%d화.episode".format(i + 1, i + 1)), text) }
        return p
    }

    /** Registers [names] as the project's character names, like opening a project with those cards does. */
    private fun <T> withNames(vararg names: String, block: () -> T): T {
        DefaultMorphemeAnalyzer.setProjectWords(names.toList())
        Morphology.awaitDictionary()
        try {
            return block()
        } finally {
            DefaultMorphemeAnalyzer.setProjectWords(emptyList())
            Morphology.awaitDictionary()
        }
    }

    // --- 3-7 count by morpheme

    @Test
    fun countsWholeMorphemesOnly() {
        // 연아 is inside 서연아 (서연 + 아), 하늘 inside 하늘색: neither is a mention.
        assertEquals(0, CharacterUsage.count("서연아 이리 와.", listOf("연아")))
        assertEquals(1, CharacterUsage.count("서연아 이리 와.", listOf("서연")))
        assertEquals(1, CharacterUsage.count("하늘색 옷을 입었다. 하늘이 맑았다.", listOf("하늘")))
        assertEquals(2, CharacterUsage.count("서연은 웃었다. 서연이 왔다.", listOf("서연", "연아")))
    }

    @Test
    fun longerNamesWinAndDirectivesAreNotText() {
        assertEquals(2, CharacterUsage.count("김민준이 왔다. 민준이 웃었다.", listOf("김민준", "민준")))
        assertEquals(1, CharacterUsage.count("@메모(\"서연 대사 고치기\")\n서연은 웃었다.", listOf("서연")))
        assertEquals(0, CharacterUsage.count("", listOf("서연")))
        assertEquals(0, CharacterUsage.count("서연은 웃었다.", emptyList()))
    }

    @Test
    fun appearancesAreCountedPerChapter() {
        val p = project("서연은 웃었다. 서연이 왔다.", "아무도 없다.", "하늘색 옷의 서연아, 이리 와.")
        val sheet = CharacterSheet("서연", listOf("연아"), "", p.charactersDir.resolve("서연.character"))
        val appearances = CharacterUsage.appearances(p, sheet)
        assertEquals(listOf("제1화", "제3화"), appearances.map { it.title })
        assertEquals(listOf(2, 1), appearances.map { it.count })
    }

    // --- 3-5 suggestions

    @Test
    fun suggestsNamesWithoutACardMostUsedFirst(): Unit = withNames("카이든", "루시안", "에단") {
        val p = project(
            "카이든은 웃었다. 카이든이 왔다. 카이든을 봤다. 루시안을 봤다.",
            "카이든은 떠났다. 루시안이 말했다. 루시안은 갔다. 에단이 왔다.",
        )
        // 카이든 4 times in 2 chapters, 루시안 3 times in 2 chapters; 에단 only once, below the default of 3.
        assertEquals(listOf(CharacterSuggestion("카이든", 4, 2), CharacterSuggestion("루시안", 3, 2)), CharacterSuggestions.suggest(p))
        assertEquals(listOf("카이든"), CharacterSuggestions.suggest(p, minCount = 4).map { it.name })
        assertEquals(listOf("카이든", "루시안", "에단"), CharacterSuggestions.suggest(p, minCount = 1).map { it.name })
    }

    @Test
    fun namesWithACardAreNotSuggested(): Unit = withNames("카이든", "루시안", "에단") {
        val p = project("카이든은 웃었다. 카이든이 왔다. 카이든을 봤다. 루시안이 왔다. 루시안은 갔다. 루시안을 봤다.")
        assertEquals(setOf("카이든", "루시안"), CharacterSuggestions.suggest(p).map { it.name }.toSet())

        Files.writeString(p.charactersDir.resolve("카이든.character"), "별칭: \n주인공")
        assertEquals(listOf("루시안"), CharacterSuggestions.suggest(p).map { it.name })
        // An alias counts as known too, and so does a world card.
        Files.writeString(p.worldDir.resolve("학원.world"), "별칭: 루시안\n배경")
        assertTrue(CharacterSuggestions.suggest(p).isEmpty())
    }

    @Test
    fun noManuscriptMeansNoSuggestions() {
        assertTrue(CharacterSuggestions.suggest(project()).isEmpty())
        assertTrue(CharacterSuggestions.suggest(project("")).isEmpty())
        assertTrue(CharacterSuggestions.suggest(project("카이든")).isEmpty())
    }
}
