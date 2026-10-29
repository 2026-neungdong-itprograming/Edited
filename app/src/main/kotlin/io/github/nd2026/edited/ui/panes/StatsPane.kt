package io.github.nd2026.edited.ui.panes

import io.github.nd2026.edited.event.InputEvent
import io.github.nd2026.edited.project.Project
import io.github.nd2026.edited.stats.ChapterStats
import io.github.nd2026.edited.stats.ManuscriptStats
import io.github.nd2026.edited.ui.Widget
import java.awt.Graphics2D
import java.awt.Rectangle

/** Per-chapter length table with a progress bar against [goalNoSpace] (characters without spaces). */
class StatsPane : Widget() {
    var project: Project? = null
        set(value) { field = value; refresh() }

    var goalNoSpace: Int = 5000
        set(value) { field = value; requestRepaint() }

    var chapters: List<ChapterStats> = emptyList()
        private set

    private var firstRow = 0

    /** Re-reads the chapters from disk; call after saving. */
    fun refresh() {
        chapters = project?.let { runCatching { ManuscriptStats.of(it) }.getOrDefault(emptyList()) }.orEmpty()
        firstRow = firstRow.coerceIn(0, (chapters.size - 1).coerceAtLeast(0))
        requestRepaint()
    }

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

        val barWidth = (bounds.width - 28).coerceAtLeast(10)
        chapters.drop(firstRow).forEachIndexed { i, chapter ->
            val top = 100 + i * ROW_HEIGHT
            if (top > bounds.height) return
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
    }

    override fun onMouseEvent(event: InputEvent.MouseInput): Boolean {
        if (event !is InputEvent.Scroll) return false
        firstRow = (firstRow + PaneStyle.scrollRows(event.unitsToScroll)).coerceIn(0, (chapters.size - 1).coerceAtLeast(0))
        requestRepaint()
        return true
    }

    private companion object {
        const val ROW_HEIGHT = 56
    }
}
