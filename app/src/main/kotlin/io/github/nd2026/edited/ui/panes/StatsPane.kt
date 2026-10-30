package io.github.nd2026.edited.ui.panes

import io.github.nd2026.edited.event.InputEvent
import io.github.nd2026.edited.lexer.Morphology
import io.github.nd2026.edited.project.Project
import io.github.nd2026.edited.stats.ChapterStats
import io.github.nd2026.edited.stats.ManuscriptStats
import io.github.nd2026.edited.stats.WordCount
import io.github.nd2026.edited.stats.WordStats
import io.github.nd2026.edited.ui.Widget
import java.awt.Graphics2D
import java.awt.Rectangle
import java.nio.file.Files
import java.nio.file.Path

/** Word statistics of one chapter, shown under the table when its row is picked (2-7). */
data class WordDetail(val path: Path, val topWords: List<WordCount>, val adverbsPer100: Int, val analyzed: Boolean)

/**
 * Per-chapter length table with a progress bar against [goalNoSpace] (characters without spaces). Clicking a
 * chapter row opens "자주 쓴 낱말 Top 10" and "부사 100개당 N개" for it below the table; the analysis runs in the
 * background, so the pane never waits on Kiwi.
 */
class StatsPane : Widget() {
    var project: Project? = null
        set(value) { field = value; refresh() }

    var goalNoSpace: Int = 5000
        set(value) { field = value; requestRepaint() }

    var chapters: List<ChapterStats> = emptyList()
        private set

    /** The chapter whose word statistics are shown, or null. */
    var selectedChapter: Path? = null
        private set

    /** Statistics of [selectedChapter]; null while they are being computed. */
    var detail: WordDetail? = null
        private set

    private var firstRow = 0
    private val work = BackgroundWork()

    /** Waits for the running word analysis (tests only; the app has an event loop to wait on). */
    internal fun awaitIdle(timeoutMillis: Long = 30_000) = work.awaitIdle(timeoutMillis)

    /** Re-reads the chapters from disk; call after saving. The open word statistics are recomputed too. */
    fun refresh() {
        chapters = project?.let { runCatching { ManuscriptStats.of(it) }.getOrDefault(emptyList()) }.orEmpty()
        firstRow = firstRow.coerceIn(0, (chapters.size - 1).coerceAtLeast(0))
        select(selectedChapter?.takeIf { path -> chapters.any { it.path == path } })
        requestRepaint()
    }

    /** Shows the word statistics of the chapter at [path] (null closes them). */
    fun select(path: Path?) {
        selectedChapter = path
        detail = null
        if (path == null) {
            work.cancel()
        } else {
            work.run({ compute(path) }) { detail = it; requestRepaint() }
        }
        requestRepaint()
    }

    private fun compute(path: Path): WordDetail {
        val text = Files.readString(path)
        val analyzed = Morphology.isAvailable
        return WordDetail(path, WordStats.topWords(text, TOP_WORDS), WordStats.posRatio(text).adverbsPer100, analyzed)
    }

    private val detailHeight get() = if (selectedChapter == null) 0 else DETAIL_HEIGHT.coerceAtMost(bounds.height / 2)

    override fun onPaint(g: Graphics2D, localRegion: Rectangle) {
        PaneStyle.paintPanel(this, g, "분량  (목표 공백 제외 %,d자)".format(goalNoSpace))
        g.font = theme.labelFont
        val fm = g.fontMetrics
        if (chapters.isEmpty()) {
            g.color = theme.outline; g.drawString("원고가 없습니다.", 14, 64); return
        }
        val total = ManuscriptStats.total(chapters)
        g.color = theme.onSurface
        g.drawString("전체 %,d자 · 공백 제외 %,d자".format(total.chars, total.charsNoSpace), 14, 60)
        g.color = theme.outline
        g.drawString("원고지 %,d매 · %d화 · %,d문장".format(ManuscriptStats.manuscriptPages(total.chars), chapters.size, total.sentences), 14, 80)
        g.drawLine(0, 92, bounds.width, 92)

        val listBottom = bounds.height - detailHeight
        val clip = g.clip
        g.clipRect(0, 93, bounds.width, (listBottom - 93).coerceAtLeast(0))
        val barWidth = (bounds.width - 28).coerceAtLeast(10)
        chapters.drop(firstRow).forEachIndexed { i, chapter ->
            val top = FIRST_ROW_TOP + i * ROW_HEIGHT
            if (top > listBottom) return@forEachIndexed
            if (chapter.path == selectedChapter) {
                g.color = PaneStyle.blend(theme.surface, theme.primary, .14f)
                g.fillRoundRect(6, top - 2, bounds.width - 12, ROW_HEIGHT - 4, 8, 8)
            }
            val ratio = if (goalNoSpace > 0) chapter.charsNoSpace / goalNoSpace.toFloat() else 0f
            g.color = theme.onSurface
            g.drawString(PaneStyle.ellipsize(chapter.title, fm, bounds.width - 28), 14, top + 16)
            g.color = theme.outline
            g.drawString("%,d / %,d자  (%d%%)".format(chapter.charsNoSpace, goalNoSpace, (ratio * 100).toInt()), 14, top + 34)
            g.color = PaneStyle.blend(theme.surface, theme.outline, .25f)
            g.fillRoundRect(14, top + 40, barWidth, 6, 6, 6)
            g.color = if (ratio >= 1f) theme.primary else theme.warning
            g.fillRoundRect(14, top + 40, (barWidth * ratio.coerceAtMost(1f)).toInt(), 6, 6, 6)
        }
        g.clip = clip
        if (selectedChapter != null) paintDetail(g, listBottom)
    }

    /** "자주 쓴 낱말 Top 10" in two columns and the adverb ratio, under the table. */
    private fun paintDetail(g: Graphics2D, top: Int) {
        val fm = g.fontMetrics
        g.color = theme.separator
        g.drawLine(0, top, bounds.width, top)
        val title = chapters.firstOrNull { it.path == selectedChapter }?.title.orEmpty()
        g.color = theme.onSurface
        g.drawString(PaneStyle.ellipsize("$title · 자주 쓴 낱말 Top $TOP_WORDS", fm, bounds.width - 28), 14, top + 22)
        val detail = detail
        val message = when {
            detail == null -> "분석하는 중…"
            !detail.analyzed -> "형태소 분석을 쓸 수 없습니다"
            detail.topWords.isEmpty() -> "분석할 낱말이 없습니다"
            else -> null
        }
        g.color = theme.outline
        if (message != null || detail == null) {
            g.drawString(message.orEmpty(), 14, top + 46)
            return
        }
        val columnWidth = (bounds.width - 28) / 2
        detail.topWords.forEachIndexed { i, word ->
            val x = 14 + (i / COLUMN_ROWS) * columnWidth
            val y = top + 46 + (i % COLUMN_ROWS) * 18
            g.color = theme.onSurface
            val label = PaneStyle.ellipsize("${i + 1}. ${word.lemma}", fm, columnWidth - 44)
            g.drawString(label, x, y)
            g.color = theme.outline
            val count = "${word.count}회"
            g.drawString(count, x + columnWidth - 12 - fm.stringWidth(count), y)
        }
        g.color = theme.primary
        g.drawString("부사 100개당 ${detail.adverbsPer100}개", 14, top + 46 + COLUMN_ROWS * 18 + 6)
    }

    override fun onMouseEvent(event: InputEvent.MouseInput): Boolean {
        when (event) {
            is InputEvent.Scroll -> {
                firstRow = (firstRow + PaneStyle.scrollRows(event.unitsToScroll)).coerceIn(0, (chapters.size - 1).coerceAtLeast(0))
                requestRepaint()
                return true
            }
            is InputEvent.MousePressed -> {
                val localY = event.y - bounds.y
                if (localY < FIRST_ROW_TOP - 2 || localY >= bounds.height - detailHeight) return false
                val chapter = chapters.getOrNull(firstRow + (localY - (FIRST_ROW_TOP - 2)) / ROW_HEIGHT) ?: return false
                select(chapter.path)
                return true
            }
            else -> return false
        }
    }

    private companion object {
        const val ROW_HEIGHT = 56
        const val FIRST_ROW_TOP = 100
        const val TOP_WORDS = 10
        const val COLUMN_ROWS = 5
        const val DETAIL_HEIGHT = 46 + COLUMN_ROWS * 18 + 22
    }
}
