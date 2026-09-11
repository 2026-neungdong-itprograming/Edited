package io.github.nd2026.edited

import io.github.nd2026.edited.core.TextArea
import io.github.nd2026.edited.event.InputEvent
import io.github.nd2026.edited.theme.MaterialDarkTheme
import io.github.nd2026.edited.theme.MaterialLightTheme
import io.github.nd2026.edited.ui.Container
import io.github.nd2026.edited.ui.OverlayHostWidget
import io.github.nd2026.edited.ui.RootPane
import io.github.nd2026.edited.ui.TextAreaWidget
import io.github.nd2026.edited.ui.Widget
import io.github.nd2026.edited.ui.components.TabBarWidget
import io.github.nd2026.edited.ui.components.TabItem
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics2D
import java.awt.Rectangle
import javax.swing.JFrame
import javax.swing.SwingUtilities

fun main() = SwingUtilities.invokeLater { Main().isVisible = true }

class Main : JFrame() {
    init {
        title = "Texted — Material UI Mockup"
        size = Dimension(1280, 800)
        minimumSize = Dimension(900, 600)
        defaultCloseOperation = EXIT_ON_CLOSE
        setLocationRelativeTo(null)
        contentPane.add(RootPane().apply {
            content = OverlayHostWidget(MockWorkspaceWidget())
        })
    }
}

/** Executable test composition for the custom-painted Material widget toolkit. */
private class MockWorkspaceWidget : Container() {
    private val topBar = MockTopBarWidget()
    private val toolStrip = MockToolStripWidget()
    private val project = MockToolPaneWidget("Project", listOf(
        "▾  달빛 아래의 계약", "    01. 낯선 손님.md", "    02. 깨진 약속.md",
        "    03. 귀환.md", "▸  인물 설정", "▸  세계관",
    ))
    private val editor = MockEditorPaneWidget()
    private val inspector = MockToolPaneWidget("Characters", listOf(
        "윤서진", "  상태   생존", "  위치   북부 관문", "  감정   경계 72", "",
        "관계", "  한도윤  동료 → 의심", "  이채린  미확인",
    ))
    private val problems = MockProblemsWidget()
    private val statusBar = MockStatusBarWidget()

    init {
        listOf(topBar, toolStrip, project, editor, inspector, problems, statusBar).forEach(::addChild)
    }

    override fun layout() {
        val b = bounds
        val topHeight = 56
        val statusHeight = 24
        val railWidth = 52
        val bottomHeight = (b.height * 0.23).toInt().coerceIn(140, 190)
        val leftWidth = if (b.width >= 1050) 238 else 190
        val rightWidth = if (b.width >= 1120) 260 else 0
        val contentTop = b.y + topHeight
        val contentBottom = b.y + b.height - statusHeight
        val mainBottom = contentBottom - bottomHeight
        val editorLeft = b.x + railWidth + leftWidth
        val editorRight = b.x + b.width - rightWidth

        topBar.setBounds(b.x, b.y, b.width, topHeight)
        toolStrip.setBounds(b.x, contentTop, railWidth, contentBottom - contentTop)
        project.setBounds(b.x + railWidth, contentTop, leftWidth, mainBottom - contentTop)
        editor.setBounds(editorLeft, contentTop, (editorRight - editorLeft).coerceAtLeast(1), mainBottom - contentTop)
        inspector.visible = rightWidth > 0
        if (rightWidth > 0) inspector.setBounds(editorRight, contentTop, rightWidth, mainBottom - contentTop)
        problems.setBounds(b.x + railWidth, mainBottom, b.width - railWidth, bottomHeight)
        statusBar.setBounds(b.x, contentBottom, b.width, statusHeight)
    }

    override fun onPaint(g: Graphics2D, localRegion: Rectangle) {
        g.color = theme.background
        g.fillRect(0, 0, bounds.width, bounds.height)
    }
}

private class MockTopBarWidget : Widget() {
    override fun onPaint(g: Graphics2D, localRegion: Rectangle) {
        g.color = theme.surface
        g.fillRect(0, 0, bounds.width, bounds.height)
        g.color = theme.outline
        g.drawLine(0, bounds.height - 1, bounds.width, bounds.height - 1)
        g.font = theme.bodyFont.deriveFont(18f)
        g.color = theme.primary
        g.drawString("T", 20, 35)
        g.color = theme.onSurface
        g.drawString("Texted", 44, 35)

        val searchWidth = minOf(420, (bounds.width * 0.38).toInt())
        val searchX = (bounds.width - searchWidth) / 2
        g.color = blend(theme.surface, theme.primary, 0.08f)
        g.fillRoundRect(searchX, 10, searchWidth, 36, 24, 24)
        g.font = theme.labelFont
        g.color = theme.outline
        g.drawString("⌕  작품, 인물, 사건 검색     Ctrl+K", searchX + 16, 33)
        g.color = theme.onSurface
        g.drawString("Git: main", bounds.width - 190, 33)
        g.drawString(if (theme.name.contains("Dark")) "☀" else "☾", bounds.width - 48, 34)
    }

    override fun onMouseEvent(event: InputEvent.MouseInput): Boolean {
        if (event !is InputEvent.MousePressed || event.x < bounds.x + bounds.width - 72) return false
        val pane = hostPane ?: return false
        pane.themeProvider.current = if (pane.themeProvider.current.name.contains("Dark")) MaterialLightTheme else MaterialDarkTheme
        pane.scheduler.requestRepaint(Rectangle(0, 0, pane.width, pane.height))
        return true
    }
}

private class MockToolStripWidget : Widget() {
    private val labels = listOf("P", "S", "C", "G", "H")
    private var selectedIndex = 0

    override fun onPaint(g: Graphics2D, localRegion: Rectangle) {
        g.color = blend(theme.surface, theme.primary, 0.035f)
        g.fillRect(0, 0, bounds.width, bounds.height)
        g.color = theme.outline
        g.drawLine(bounds.width - 1, 0, bounds.width - 1, bounds.height)
        g.font = theme.labelFont.deriveFont(13f)
        labels.forEachIndexed { index, label ->
            val y = 14 + index * 48
            if (index == selectedIndex) {
                g.color = blend(theme.surface, theme.primary, 0.18f)
                g.fillRoundRect(8, y, 36, 36, 18, 18)
                g.color = theme.primary
            } else g.color = theme.outline
            g.drawString(label, 26 - g.fontMetrics.stringWidth(label) / 2, y + 23)
        }
    }

    override fun onMouseEvent(event: InputEvent.MouseInput): Boolean {
        if (event !is InputEvent.MousePressed) return false
        val index = (event.y - bounds.y - 14) / 48
        if (index !in labels.indices) return false
        selectedIndex = index
        requestRepaint()
        return true
    }
}

private open class MockToolPaneWidget(
    private val title: String,
    private val sections: List<String>,
) : Widget() {
    override fun onPaint(g: Graphics2D, localRegion: Rectangle) {
        g.color = theme.surface
        g.fillRect(0, 0, bounds.width, bounds.height)
        g.color = theme.outline
        g.drawRect(0, 0, bounds.width - 1, bounds.height - 1)
        g.font = theme.labelFont.deriveFont(13f)
        g.color = theme.onSurface
        g.drawString(title, 14, 25)
        g.color = theme.outline
        g.drawString("⋮", bounds.width - 24, 25)
        g.drawLine(0, 38, bounds.width, 38)
        g.font = theme.labelFont
        var y = 62
        sections.forEach { line ->
            g.color = if (line.startsWith("  ")) theme.outline else theme.onSurface
            g.drawString(line, 14, y)
            y += 25
        }
    }
}

private class MockEditorPaneWidget : Container() {
    private val documents = linkedMapOf(
        "chapter-1" to TextAreaWidget(TextArea(CHAPTER_ONE)),
        "chapter-2" to TextAreaWidget(TextArea(CHAPTER_TWO)),
    )
    private val tabs = TabBarWidget(listOf(
        TabItem("chapter-1", "01. 낯선 손님.md"),
        TabItem("chapter-2", "02. 깨진 약속.md"),
    ))
    private var activeEditor = documents.getValue("chapter-1")

    init {
        addChild(tabs)
        addChild(activeEditor)
        tabs.onSelect = ::selectDocument
        tabs.onClose = ::closeDocument
    }

    override fun layout() {
        val b = bounds
        tabs.setBounds(b.x, b.y, b.width, 40)
        activeEditor.setBounds(b.x, b.y + 40, b.width, (b.height - 40).coerceAtLeast(1))
    }

    override fun onPaint(g: Graphics2D, localRegion: Rectangle) {
        g.color = theme.surface
        g.fillRect(0, 0, bounds.width, bounds.height)
        g.color = theme.outline
        g.drawRect(0, 0, bounds.width - 1, bounds.height - 1)
    }

    private fun selectDocument(id: String) {
        val next = documents[id] ?: return
        if (next === activeEditor) return
        removeChild(activeEditor)
        activeEditor = next
        addChild(activeEditor)
        layout()
        requestRepaint()
    }

    private fun closeDocument(id: String) {
        if (documents.size <= 1) return
        val removed = documents.remove(id) ?: return
        tabs.tabs = tabs.tabs.filterNot { it.id == id }
        if (removed === activeEditor) selectDocument(tabs.selectedId ?: return)
    }
}

private class MockProblemsWidget : Widget() {
    override fun onPaint(g: Graphics2D, localRegion: Rectangle) {
        g.color = theme.surface
        g.fillRect(0, 0, bounds.width, bounds.height)
        g.color = theme.outline
        g.drawRect(0, 0, bounds.width - 1, bounds.height - 1)
        g.font = theme.labelFont.deriveFont(13f)
        g.color = theme.onSurface
        g.drawString("Problems  3", 16, 25)
        g.color = theme.outline
        g.drawString("Search     Git History", 120, 25)
        g.drawLine(0, 38, bounds.width, 38)
        val rows = listOf(
            Triple("●", theme.error, "설정 충돌: 윤서진은 2화에서 북부 관문을 떠났습니다."),
            Triple("▲", Color(0xE6, 0x8A, 0x00), "동일 어미 ‘-었다’가 4회 연속 사용되었습니다."),
            Triple("◆", theme.primary, "회수되지 않은 복선: 은빛 열쇠"),
        )
        rows.forEachIndexed { index, (mark, color, text) ->
            val y = 66 + index * 29
            g.color = color
            g.drawString(mark, 18, y)
            g.color = theme.onSurface
            g.drawString(text, 42, y)
            g.color = theme.outline
            g.drawString("${index + 1}:${12 + index * 9}", bounds.width - 70, y)
        }
    }
}

private class MockStatusBarWidget : Widget() {
    override fun onPaint(g: Graphics2D, localRegion: Rectangle) {
        g.color = theme.primary
        g.fillRect(0, 0, bounds.width, bounds.height)
        g.font = theme.labelFont
        g.color = theme.onPrimary
        g.drawString("✓ 저장됨", 12, 17)
        val right = "UTF-8     한국어     1,284자"
        g.drawString(right, bounds.width - g.fontMetrics.stringWidth(right) - 14, 17)
    }
}

private fun blend(base: Color, overlay: Color, alpha: Float): Color {
    val inverse = 1f - alpha
    return Color(
        (base.red * inverse + overlay.red * alpha).toInt(),
        (base.green * inverse + overlay.green * alpha).toInt(),
        (base.blue * inverse + overlay.blue * alpha).toInt(),
    )
}

private val CHAPTER_ONE = """
    제1화. 낯선 손님

    비가 그친 뒤의 골목은 유난히 조용했다.
    윤서진은 젖은 외투의 깃을 세우고 오래된 여관의 문을 밀었다.

    “기다리고 있었습니다.”

    창가에 앉은 남자가 은빛 열쇠를 탁자 위에 내려놓았다.
    서진은 열쇠보다 먼저 남자의 떨리는 손끝을 보았다.

    이 열쇠를 본 것은 오늘이 처음이어야 했다.
    그런데도 손바닥의 오래된 흉터가 뜨겁게 욱신거렸다.

    ─ 북부 관문은 사흘 전에 폐쇄되었다.
    ─ 살아 돌아온 사람은 아직 없다.

    서진은 의자를 당겨 앉았다.
    “대가부터 이야기하죠.”
""".trimIndent()

private val CHAPTER_TWO = """
    제2화. 깨진 약속

    새벽 종이 세 번 울리기 전에 성벽을 넘어야 했다.
    한도윤은 약속한 장소에 나타나지 않았다.

    윤서진은 주머니 속 은빛 열쇠를 꽉 쥐었다.
    차가워야 할 금속이 심장처럼 미세하게 뛰고 있었다.

    멀리서 경비대의 횃불이 하나둘 켜졌다.
    선택할 시간은 길지 않았다.
""".trimIndent()
