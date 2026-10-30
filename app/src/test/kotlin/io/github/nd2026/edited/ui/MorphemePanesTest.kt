package io.github.nd2026.edited.ui

import io.github.nd2026.edited.event.InputEvent
import io.github.nd2026.edited.lexer.DefaultMorphemeAnalyzer
import io.github.nd2026.edited.lexer.Morphology
import io.github.nd2026.edited.project.NewProjectRequest
import io.github.nd2026.edited.project.Project
import io.github.nd2026.edited.project.ProjectService
import io.github.nd2026.edited.ui.components.ButtonWidget
import io.github.nd2026.edited.ui.panes.CharactersPane
import io.github.nd2026.edited.ui.panes.HistoryPane
import io.github.nd2026.edited.ui.panes.SearchPane
import io.github.nd2026.edited.ui.panes.StatsPane
import io.github.nd2026.edited.vcs.HistoryService
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 작업 2-7, 3-6, 4-6, 6-7/6-8: the panes that show morpheme results. */
class MorphemePanesTest {
    private fun project(vararg chapters: String): Project {
        val p = ProjectService.create(NewProjectRequest("패널", Files.createTempDirectory("texted-morph-panes").toString(), "작가")).project
        if (chapters.isNotEmpty()) Files.delete(p.manuscriptDir.resolve(ProjectService.FIRST_CHAPTER))
        chapters.forEachIndexed { i, text -> Files.writeString(p.manuscriptDir.resolve("%02d. 제%d화.episode".format(i + 1, i + 1)), text) }
        return p
    }

    private fun paint(w: Widget) {
        val image = BufferedImage(w.bounds.width, w.bounds.height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        w.paint(g, Rectangle(0, 0, w.bounds.width, w.bounds.height))
        g.dispose()
    }

    private fun Widget.press(localY: Int) = onMouseEvent(InputEvent.MousePressed(bounds.x + 20, bounds.y + localY, 1, 0))

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

    // --- 2-7 StatsPane

    @Test
    fun pickingAChapterShowsItsTopWordsAndAdverbRatio() {
        val p = project("밥을 먹었다. 빵을 먹고 갑자기 웃었다.", "아무도 없다.")
        val pane = StatsPane().apply { setBounds(52, 76, 280, 600) }
        pane.project = p
        paint(pane)
        assertEquals(2, pane.chapters.size)
        assertEquals(null, pane.detail)

        // Row 0 starts at y = 100 in the pane.
        assertTrue(pane.press(110))
        assertEquals(p.chapters().first().path, pane.selectedChapter)
        assertTrue(pane.awaitIdle())
        val detail = assertNotNull(pane.detail)
        assertEquals("먹다", detail.topWords.first().lemma)
        assertEquals(2, detail.topWords.first().count)
        assertTrue(detail.analyzed)
        // 1 adverb among 갑자기, 먹다, 먹다, 밥, 빵, 웃다 = 6 content words + the adverb counted once → 1 * 100 / 6.
        assertEquals(1 * 100 / 6, detail.adverbsPer100)
        paint(pane)

        // The second chapter's row; its words come in too. Rows above the detail area still respond.
        assertTrue(pane.press(100 + 56 + 10))
        assertEquals(p.chapters()[1].path, pane.selectedChapter)
        assertTrue(pane.awaitIdle())
        paint(pane)

        // The detail follows a refresh and closes when the chapter disappears.
        Files.writeString(p.chapters().first().path, "밥을 먹었다.")
        pane.select(p.chapters().first().path)
        pane.refresh()
        assertTrue(pane.awaitIdle())
        assertEquals(1, pane.detail?.topWords?.first { it.lemma == "먹다" }?.count)
        pane.select(null)
        assertEquals(null, pane.selectedChapter)
        assertEquals(null, pane.detail)
    }

    @Test
    fun emptyChapterShowsNoWords() {
        val p = project("")
        val pane = StatsPane().apply { setBounds(0, 0, 280, 600) }
        pane.project = p
        pane.select(p.chapters().single().path)
        assertTrue(pane.awaitIdle())
        assertTrue(assertNotNull(pane.detail).topWords.isEmpty())
        paint(pane)
    }

    // --- 3-6 CharactersPane

    @Test
    fun suggestedCharactersAppearAtTheBottomOfTheList() = withNames("카이든", "루시안") {
        val p = project("루시안은 웃었다. 루시안이 왔다. 루시안을 봤다. 카이든이 말했다.", "카이든은 갔다. 카이든을 봤다.")
        Files.writeString(p.charactersDir.resolve("카이든.character"), "별칭: \n주인공")
        val pane = CharactersPane().apply { setBounds(52, 76, 280, 600) }
        pane.project = p
        assertTrue(pane.awaitIdle())
        assertEquals(listOf("루시안"), pane.suggestions.map { it.name })
        assertEquals(3, pane.suggestions.single().count)
        paint(pane)

        // Rows: 인물 · 카이든 · 세계관 · 추천 인물 · 루시안. Picking the suggestion, then the card, both paint.
        assertTrue(pane.press(45 + 4 * 24 + 8))
        paint(pane)
        assertTrue(pane.press(45 + 1 * 24 + 8))
        assertTrue(pane.awaitIdle())
        paint(pane)
        // A section header is not selectable.
        assertEquals(false, pane.press(45 + 3 * 24 + 8))
    }

    @Test
    fun noSuggestionsWithoutAManuscript() {
        val pane = CharactersPane().apply { setBounds(0, 0, 280, 600) }
        pane.project = project("")
        assertTrue(pane.awaitIdle())
        assertTrue(pane.suggestions.isEmpty())
        paint(pane)
        pane.project = null
        assertTrue(pane.suggestions.isEmpty())
        paint(pane)
    }

    // --- 4-6 SearchPane

    @Test
    fun lemmaSearchToggleFindsEveryForm() {
        val p = project("밥을 먹었다.\n빵을 먹고 갔다.\n물을 마셨다.")
        val pane = SearchPane().apply { setBounds(52, 76, 280, 600) }
        pane.project = p
        pane.searchFor("먹다")
        assertEquals(0, pane.hits.size)
        assertEquals("먹다", pane.highlightText)

        val toggle = pane.children.filterIsInstance<ButtonWidget>().first { "기본형" in it.label }
        toggle.onClick()
        assertTrue(pane.lemmaMode)
        assertTrue(pane.awaitIdle())
        assertEquals(listOf(0 to 3, 1 to 3), pane.hits.map { it.line to it.column })
        // The editor marks nothing in this mode: the matches are other forms of the word.
        assertEquals("", pane.highlightText)
        assertTrue("✓" in toggle.label)
        paint(pane)

        // A replacement is ignored here: nothing to preview, nothing to apply.
        pane.shortcutField(replace = true).text = "씹다"
        assertTrue(pane.previews.isEmpty())
        assertTrue(pane.awaitIdle())
        assertEquals(2, pane.hits.size)

        // Back to a plain search: "먹다" is not in the text as written.
        toggle.onClick()
        assertEquals(false, pane.lemmaMode)
        assertEquals(0, pane.hits.size)
        assertTrue(pane.awaitIdle())
        assertEquals(0, pane.hits.size)
    }

    @Test
    fun lemmaSearchOfNothing() {
        val pane = SearchPane().apply { setBounds(0, 0, 280, 600) }
        pane.project = project("밥을 먹었다.")
        pane.lemmaMode = true
        pane.searchFor("없는낱말")
        assertTrue(pane.awaitIdle())
        assertTrue(pane.hits.isEmpty())
        pane.searchFor("")
        assertTrue(pane.hits.isEmpty())
        paint(pane)
    }

    // --- 6-7 / 6-8 HistoryPane

    @Test
    fun changedSentencesGetAKindTagAndTheChangedWords() {
        val p = project("")
        val chapter = p.chapters().single().path
        Files.writeString(chapter, "그는 문을 열었다. 민준이 웃었다. 그는 밥을 먹었다.")
        assertNotNull(HistoryService(p.root).commitAll("첫 저장"))
        Files.writeString(chapter, "그는 창문을 열었다. 민준은 웃었다. 그는 밥을 먹는다.")

        val pane = HistoryPane().apply { setBounds(52, 76, 280, 600) }
        pane.project = p
        pane.activePath = chapter
        // Newest version first: row 0 spans y 45..69.
        assertTrue(pane.press(50))
        assertTrue(pane.awaitIdle())
        assertEquals(listOf("[낱말]  문 → 창문", "[조사]", "[어미]"), pane.noteTexts())
        paint(pane)
    }

    @Test
    fun addedAndRemovedSentencesHaveNoNote() {
        val p = project("")
        val chapter = p.chapters().single().path
        Files.writeString(chapter, "하나. 둘.")
        HistoryService(p.root).commitAll("첫 저장")
        Files.writeString(chapter, "하나. 둘. 셋.")
        val pane = HistoryPane().apply { setBounds(0, 0, 280, 600) }
        pane.project = p
        pane.activePath = chapter
        pane.press(50)
        assertTrue(pane.awaitIdle())
        assertTrue(pane.noteTexts().isEmpty())
        paint(pane)
    }
}
