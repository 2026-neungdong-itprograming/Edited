package io.github.nd2026.edited.ui

import io.github.nd2026.edited.AppInfo
import io.github.nd2026.edited.event.InputEvent
import io.github.nd2026.edited.lexer.KiwiModelDownloader
import io.github.nd2026.edited.ui.components.ContextMenuWidget
import io.github.nd2026.edited.ui.panes.PaneStyle
import java.awt.Font
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.KeyEvent

/**
 * Overlay holding one menu-bar dropdown. Unlike `dismissOnOutside`, the pointer may hover the menu
 * bar ([passThrough]) without closing the menu, so moving across the titles switches menus the way
 * Windows menus do. Any other press closes the menu, and Left/Right move to the neighbouring menu.
 */
internal class MenuLayer(
    val menu: ContextMenuWidget,
    private val passThrough: (x: Int, y: Int) -> Boolean,
    private val onOutsidePress: () -> Unit,
    private val onStep: (Int) -> Unit,
) : Widget() {
    init {
        addChild(menu)
    }

    override fun hitTest(x: Int, y: Int): Widget? {
        if (!visible || !bounds.contains(x, y)) return null
        menu.hitTest(x, y)?.let { return it }
        return if (passThrough(x, y)) null else this
    }

    override fun onMouseEvent(event: InputEvent.MouseInput): Boolean {
        if (event is InputEvent.MousePressed) onOutsidePress()
        return event is InputEvent.MousePressed || event is InputEvent.MouseReleased
    }

    // The focused menu doesn't handle Left/Right, so they bubble up here.
    override fun onKeyEvent(event: InputEvent.KeyInput): Boolean {
        if (event !is InputEvent.KeyPressed) return false
        when (event.keyCode) {
            KeyEvent.VK_LEFT -> onStep(-1)
            KeyEvent.VK_RIGHT -> onStep(1)
            else -> return false
        }
        return true
    }
}

/** Plain lines of text for simple dialogs (help, about); lines starting with two spaces are dimmed. */
internal class TextLinesWidget(private val lines: List<String>) : Widget() {
    override fun onPaint(g: Graphics2D, localRegion: Rectangle) {
        g.font = theme.labelFont
        lines.forEachIndexed { i, line ->
            g.color = if (line.startsWith("  ")) theme.outline else theme.onSurface
            val tab = line.indexOf('\t')
            if (tab < 0) g.drawString(line, 0, 16 + i * LINE_HEIGHT)
            else {
                g.drawString(line.substring(0, tab), 0, 16 + i * LINE_HEIGHT)
                g.color = theme.outline
                g.drawString(line.substring(tab + 1), 230, 16 + i * LINE_HEIGHT)
            }
        }
    }

    companion object {
        const val LINE_HEIGHT = 22
        fun heightFor(lines: List<String>) = lines.size * LINE_HEIGHT + 4
    }
}

/**
 * Body of 도움말 › Texted 정보, laid out like Windows' `winver`: a large product banner, a divider,
 * then name, version (build) and copyright, the licence notice and who the copy is licensed to.
 */
internal class AboutWidget : Widget() {
    private val banner = AppInfo.NAME
    private val paragraphs = listOf(
        listOf(AppInfo.NAME, AppInfo.VERSION_LINE, AppInfo.COPYRIGHT),
        listOf(
            "${AppInfo.NAME}는 ${AppInfo.DESCRIPTION}입니다. 이 프로그램은 ${AppInfo.LICENSE}에 따라 배포되는 자유 소프트웨어이며, 어떠한 보증도 제공하지 않습니다.",
        ),
        listOf(
            "이 제품은 다음 사용자에게 사용이 허가되었습니다:",
            "    ${System.getProperty("user.name").orEmpty()}",
        ),
        listOf(
            "타사 구성 요소: Kiwi ${KiwiModelDownloader.VERSION} (LGPL-3.0), Noto Sans KR (SIL OFL 1.1), Eclipse JGit (EDL-1.0)",
            "Java ${System.getProperty("java.version")} · 출시일 ${AppInfo.RELEASE_DATE}",
        ),
    )

    override fun onPaint(g: Graphics2D, localRegion: Rectangle) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        // Banner: app mark + product name, centred like the Windows logo in winver.
        g.font = theme.bodyFont.deriveFont(Font.PLAIN, 34f)
        val bannerWidth = MARK + 14 + g.fontMetrics.stringWidth(banner)
        val left = (bounds.width - bannerWidth) / 2
        g.color = theme.primary
        g.fillRoundRect(left, 6, MARK, MARK, 10, 10)
        g.color = theme.onPrimary
        g.font = theme.bodyFont.deriveFont(Font.BOLD, 26f)
        g.drawString("T", left + (MARK - g.fontMetrics.stringWidth("T")) / 2, 6 + (MARK + g.fontMetrics.ascent - g.fontMetrics.descent) / 2)
        g.color = theme.onSurface
        g.font = theme.bodyFont.deriveFont(Font.PLAIN, 34f)
        g.drawString(banner, left + MARK + 14, 6 + (MARK + g.fontMetrics.ascent - g.fontMetrics.descent) / 2)
        g.color = theme.separator
        g.drawLine(0, DIVIDER_Y, bounds.width, DIVIDER_Y)

        g.font = theme.labelFont.deriveFont(13f)
        val fm = g.fontMetrics
        var y = DIVIDER_Y + 14
        for ((index, paragraph) in paragraphs.withIndex()) {
            g.color = if (index == paragraphs.lastIndex) theme.outline else theme.onSurface
            for (line in paragraph.flatMap { wrapWords(it, fm, bounds.width) }) {
                y += LINE
                g.drawString(line, 0, y)
            }
            y += PARAGRAPH_GAP
        }
    }

    /** Wraps at spaces (so "v3.0" or a URL never splits), falling back to characters for over-long words. */
    private fun wrapWords(text: String, fm: java.awt.FontMetrics, width: Int): List<String> {
        val lines = ArrayList<String>()
        var line = ""
        for (word in text.split(' ')) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            when {
                fm.stringWidth(candidate) <= width -> line = candidate
                line.isEmpty() -> PaneStyle.wrap(word, fm, width).let { lines += it.dropLast(1); line = it.last() }
                else -> { lines += line; line = word }
            }
        }
        lines += line
        return lines
    }

    companion object {
        private const val MARK = 44
        private const val DIVIDER_Y = 64
        private const val LINE = 20
        private const val PARAGRAPH_GAP = 12
        const val HEIGHT = 330
    }
}
