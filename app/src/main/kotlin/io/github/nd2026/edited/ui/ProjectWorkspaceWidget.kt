package io.github.nd2026.edited.ui

import io.github.nd2026.edited.core.TextArea
import io.github.nd2026.edited.core.TextAreaListener
import io.github.nd2026.edited.core.TextEdit
import io.github.nd2026.edited.event.InputEvent
import io.github.nd2026.edited.project.Project
import io.github.nd2026.edited.project.ProjectNode
import io.github.nd2026.edited.theme.MaterialDarkTheme
import io.github.nd2026.edited.theme.MaterialLightTheme
import io.github.nd2026.edited.ui.components.ButtonWidget
import io.github.nd2026.edited.ui.components.TabBarWidget
import io.github.nd2026.edited.ui.components.TabItem
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Rectangle
import java.nio.file.Files
import java.nio.file.Path

class WorkspaceDocument(val path: Path) {
    val textArea = TextArea(Files.readString(path))
    var dirty = false
        private set

    init {
        textArea.addListener(object : TextAreaListener {
            override fun onTextChanged(edit: TextEdit) { dirty = true }
        })
    }

    fun save() {
        Files.writeString(path, textArea.snapshot())
        dirty = false
    }
}

/** Owns the opened project and documents without depending on rendering classes. */
class WorkspaceSession(val project: Project) {
    private val documents = linkedMapOf<Path, WorkspaceDocument>()
    var activePath: Path? = null
        private set
    val openDocuments get() = documents.values.toList()
    val activeDocument get() = activePath?.let(documents::get)

    init { project.chapters().firstOrNull()?.path?.let(::open) }

    fun open(path: Path): WorkspaceDocument {
        val normalized = path.toAbsolutePath().normalize()
        require(normalized.startsWith(project.root.toAbsolutePath().normalize())) { "프로젝트 밖의 파일입니다." }
        require(Files.isRegularFile(normalized)) { "파일이 아닙니다: $path" }
        return documents.getOrPut(normalized) { WorkspaceDocument(normalized) }.also { activePath = normalized }
    }

    fun select(path: Path): Boolean {
        if (path !in documents) return false
        activePath = path
        return true
    }

    fun close(path: Path) {
        documents.remove(path)
        if (activePath == path) activePath = documents.keys.lastOrNull()
    }

    fun saveActive(): Boolean = activeDocument?.let { it.save(); true } ?: false
}

/** Main application composition backed by an actual on-disk project. */
class ProjectWorkspaceWidget(initialSession: WorkspaceSession?) : Container() {
    private val topBar = TopBarWidget()
    private val rail = ToolRailWidget()
    private val newProjectButton = ButtonWidget("＋ 새 프로젝트")
    private val openProjectButton = ButtonWidget("폴더 열기")
    private val tree = ProjectTreeWidget()
    private val editor = DocumentEditorWidget()
    private val inspector = ProjectInspectorWidget()
    private val problems = ProblemsWidget()
    private val status = StatusBarWidget()
    private var session: WorkspaceSession? = null
    var onNewProject: () -> Unit = {}
    var onOpenProject: () -> Unit = {}

    init {
        listOf(topBar, newProjectButton, openProjectButton, rail, tree, editor, inspector, problems, status).forEach(::addChild)
        newProjectButton.onClick = { onNewProject() }
        openProjectButton.onClick = { onOpenProject() }
        tree.onOpenFile = ::openFile
        initialSession?.let { bind(it, emptyList()) } ?: showWelcome()
    }

    fun openProject(project: Project, warnings: List<String> = emptyList()) = bind(WorkspaceSession(project), warnings)

    fun saveActiveDocument() {
        val saved = runCatching { session?.saveActive() == true }.getOrDefault(false)
        status.message = if (saved) "✓ 저장됨" else "저장할 문서가 없습니다"
        editor.refresh()
        status.requestRepaint()
    }

    private fun bind(next: WorkspaceSession, warnings: List<String>) {
        session = next
        topBar.projectTitle = next.project.title
        tree.project = next.project
        editor.session = next
        inspector.project = next.project
        problems.project = next.project
        status.message = warnings.firstOrNull()?.let { "⚠ $it" } ?: "✓ ${next.project.root}"
        requestRepaint()
    }

    private fun showWelcome() {
        topBar.projectTitle = "프로젝트 없음"
        status.message = "새 프로젝트를 만들거나 실행 인수로 프로젝트 폴더를 지정하세요"
    }

    private fun openFile(path: Path) {
        val current = session ?: return
        runCatching { current.open(path) }
            .onSuccess { editor.refresh(); status.message = path.fileName.toString() }
            .onFailure { status.message = "⚠ ${it.message}" }
        status.requestRepaint()
    }

    override fun layout() {
        val b = bounds
        val topH = 76
        val statusH = 24
        val railW = 52
        val bottomH = (b.height * .23).toInt().coerceIn(140, 190)
        val leftW = if (b.width >= 1050) 238 else 190
        val rightW = if (b.width >= 1120) 260 else 0
        val contentTop = b.y + topH
        val contentBottom = b.y + b.height - statusH
        val mainBottom = contentBottom - bottomH
        val editorLeft = b.x + railW + leftW
        val editorRight = b.x + b.width - rightW
        topBar.setBounds(b.x, b.y, b.width, topH)
        val newWidth = newProjectButton.measure().width
        newProjectButton.setBounds(b.x + 12, b.y + 37, newWidth, ButtonWidget.HEIGHT)
        openProjectButton.measure().let { openProjectButton.setBounds(b.x + 20 + newWidth, b.y + 37, it.width, ButtonWidget.HEIGHT) }
        rail.setBounds(b.x, contentTop, railW, contentBottom - contentTop)
        tree.setBounds(b.x + railW, contentTop, leftW, mainBottom - contentTop)
        editor.setBounds(editorLeft, contentTop, (editorRight - editorLeft).coerceAtLeast(1), mainBottom - contentTop)
        inspector.visible = rightW > 0
        if (rightW > 0) inspector.setBounds(editorRight, contentTop, rightW, mainBottom - contentTop)
        problems.setBounds(b.x + railW, mainBottom, b.width - railW, bottomH)
        status.setBounds(b.x, contentBottom, b.width, statusH)
    }

    override fun onPaint(g: Graphics2D, localRegion: Rectangle) {
        g.color = theme.background
        g.fillRect(0, 0, bounds.width, bounds.height)
    }
}

private class TopBarWidget : Widget() {
    private val menus = listOf("파일", "편집", "보기", "이동", "도구", "Git", "도움말")

    var projectTitle = "프로젝트 없음"
        set(value) { field = value; requestRepaint() }

    override fun onPaint(g: Graphics2D, localRegion: Rectangle) {
        g.color = theme.surface
        g.fillRect(0, 0, bounds.width, bounds.height)

        // IDEA-style compact menu row.
        g.font = theme.labelFont
        g.color = blend(theme.surface, theme.primary, .12f)
        g.fillRoundRect(10, 5, 22, 22, 6, 6)
        g.color = theme.primary
        g.drawString("T", 17, 21)
        var menuX = 44
        menus.forEach { menu ->
            g.color = theme.onSurface
            g.drawString(menu, menuX, 21)
            menuX += g.fontMetrics.stringWidth(menu) + 18
        }
        g.color = theme.outline
        if (bounds.width >= 1100) {
            val titleText = "Texted — $projectTitle"
            g.drawString(titleText, bounds.width - g.fontMetrics.stringWidth(titleText) - 16, 21)
        }
        g.drawLine(0, 29, bounds.width, 29)

        // Main toolbar: project action, VCS context, global search and run actions.
        val searchWidth = minOf(330, (bounds.width * .28).toInt())
        val searchX = (bounds.width - searchWidth) / 2
        if (bounds.width >= 1100) {
            val projectX = 250
            val projectWidth = 190
            paintChip(g, projectX, 37, projectWidth, 31, "▾  $projectTitle")
        }
        g.color = blend(theme.surface, theme.primary, .09f)
        g.fillRoundRect(searchX, 37, searchWidth, 31, 8, 8)
        g.color = theme.outline
        g.drawRoundRect(searchX, 37, searchWidth, 31, 8, 8)
        g.drawString("⌕  전체 검색    Shift+Shift", searchX + 12, 57)

        val actions = "⑂ main    ▶   ◆   ⚙   " + if (theme.name.contains("Dark")) "☀" else "☾"
        g.color = theme.onSurface
        g.drawString(actions, bounds.width - g.fontMetrics.stringWidth(actions) - 18, 57)
        g.color = theme.outline
        g.drawLine(0, bounds.height - 1, bounds.width, bounds.height - 1)
    }

    override fun onMouseEvent(event: InputEvent.MouseInput): Boolean {
        if (event !is InputEvent.MousePressed || event.y < bounds.y + 30 || event.x < bounds.x + bounds.width - 54) return false
        val pane = hostPane ?: return false
        pane.themeProvider.current = if (pane.themeProvider.current.name.contains("Dark")) MaterialLightTheme else MaterialDarkTheme
        pane.scheduler.requestRepaint(Rectangle(0, 0, pane.width, pane.height))
        return true
    }

    private fun paintChip(g: Graphics2D, x: Int, y: Int, width: Int, height: Int, text: String) {
        g.color = blend(theme.surface, theme.primary, .055f)
        g.fillRoundRect(x, y, width, height, 8, 8)
        g.color = theme.outline
        g.drawRoundRect(x, y, width, height, 8, 8)
        g.color = theme.onSurface
        val available = width - 20
        var label = text
        while (label.length > 5 && g.fontMetrics.stringWidth(label) > available) label = label.dropLast(2) + "…"
        g.drawString(label, x + 10, y + 20)
    }
}

private class ToolRailWidget : Widget() {
    private val labels = listOf("P", "S", "C", "G", "H")
    private var selectedIndex = 0
    override fun onPaint(g: Graphics2D, localRegion: Rectangle) {
        g.color = blend(theme.surface, theme.primary, .035f); g.fillRect(0, 0, bounds.width, bounds.height)
        g.color = theme.outline; g.drawLine(bounds.width - 1, 0, bounds.width - 1, bounds.height)
        g.font = theme.labelFont.deriveFont(13f)
        labels.forEachIndexed { i, text ->
            val y = 14 + i * 48
            if (i == selectedIndex) { g.color = blend(theme.surface, theme.primary, .18f); g.fillRoundRect(8, y, 36, 36, 18, 18); g.color = theme.primary } else g.color = theme.outline
            g.drawString(text, 26 - g.fontMetrics.stringWidth(text) / 2, y + 23)
        }
    }
    override fun onMouseEvent(event: InputEvent.MouseInput): Boolean {
        if (event !is InputEvent.MousePressed) return false
        val index = (event.y - bounds.y - 14) / 48
        if (index !in labels.indices) return false
        selectedIndex = index; requestRepaint(); return true
    }
}

private data class TreeRow(val node: ProjectNode, val depth: Int)

private class ProjectTreeWidget : Widget() {
    var project: Project? = null
        set(value) { field = value; rows = value?.tree()?.flatten() ?: emptyList(); requestRepaint() }
    var onOpenFile: (Path) -> Unit = {}
    private var rows = emptyList<TreeRow>()

    override fun onPaint(g: Graphics2D, localRegion: Rectangle) {
        paintPanel(g, "Project")
        g.font = theme.labelFont
        rows.forEachIndexed { index, row ->
            val y = 62 + index * 25
            if (y <= bounds.height) {
                g.color = if (row.node is ProjectNode.Directory) theme.onSurface else theme.outline
                g.drawString("  ".repeat(row.depth) + (if (row.node is ProjectNode.Directory) "▾  " else "•  ") + row.node.name, 14, y)
            }
        }
    }

    override fun onMouseEvent(event: InputEvent.MouseInput): Boolean {
        if (event !is InputEvent.MousePressed) return false
        val file = rows.getOrNull((event.y - bounds.y - 44) / 25)?.node as? ProjectNode.File ?: return false
        onOpenFile(file.path); return true
    }

    private fun ProjectNode.Directory.flatten(depth: Int = 0): List<TreeRow> = children.flatMap {
        listOf(TreeRow(it, depth)) + if (it is ProjectNode.Directory) it.flatten(depth + 1) else emptyList()
    }
}

private class DocumentEditorWidget : Container() {
    private val tabs = TabBarWidget()
    private var activeEditor: TextAreaWidget? = null
    var session: WorkspaceSession? = null
        set(value) { field = value; refresh() }

    init {
        addChild(tabs)
        tabs.onSelect = { session?.select(Path.of(it)); refresh() }
        tabs.onClose = { session?.close(Path.of(it)); refresh() }
    }

    fun refresh() {
        val current = session
        tabs.tabs = current?.openDocuments?.map { TabItem(it.path.toString(), it.path.fileName.toString() + if (it.dirty) " •" else "") }.orEmpty()
        tabs.selectedId = current?.activePath?.toString()
        activeEditor?.let(::removeChild)
        activeEditor = current?.activeDocument?.let { TextAreaWidget(it.textArea) }
        activeEditor?.let(::addChild)
        layout(); requestRepaint()
    }

    override fun layout() {
        tabs.setBounds(bounds.x, bounds.y, bounds.width, 40)
        activeEditor?.setBounds(bounds.x, bounds.y + 40, bounds.width, (bounds.height - 40).coerceAtLeast(1))
    }
    override fun onPaint(g: Graphics2D, localRegion: Rectangle) {
        g.color = theme.surface; g.fillRect(0, 0, bounds.width, bounds.height)
        g.color = theme.outline; g.drawRect(0, 0, bounds.width - 1, bounds.height - 1)
        if (activeEditor == null) { g.font = theme.bodyFont; g.drawString("Project 패널에서 파일을 선택하세요", 24, 78) }
    }
}

private class ProjectInspectorWidget : Widget() {
    var project: Project? = null
        set(value) { field = value; requestRepaint() }
    override fun onPaint(g: Graphics2D, localRegion: Rectangle) {
        paintPanel(g, "Project info")
        val p = project ?: return
        g.font = theme.labelFont
        val lines = listOf("작품", "  ${p.title}", "작가", "  ${p.manifest.author.ifBlank { "미지정" }}", "언어", "  ${p.manifest.language}", "원고", "  ${p.chapters().size}개", "형식", "  v${p.manifest.formatVersion}", "", "프로젝트 경로", "  ${p.root.fileName}")
        lines.forEachIndexed { i, text -> g.color = if (text.startsWith("  ")) theme.outline else theme.onSurface; g.drawString(text, 14, 62 + i * 25) }
    }
}

private class ProblemsWidget : Widget() {
    var project: Project? = null
        set(value) { field = value; requestRepaint() }
    override fun onPaint(g: Graphics2D, localRegion: Rectangle) {
        val messages = buildList { project?.let { if (it.chapters().isEmpty()) add("원고 파일이 없습니다."); if (it.manifest.author.isBlank()) add("작가명이 지정되지 않았습니다.") } }
        paintPanel(g, "Problems  ${messages.size}")
        g.font = theme.labelFont
        if (messages.isEmpty()) { g.color = theme.outline; g.drawString("발견된 프로젝트 문제가 없습니다.", 18, 68) }
        messages.forEachIndexed { i, text -> g.color = Color(0xE6, 0x8A, 0); g.drawString("▲", 18, 68 + i * 29); g.color = theme.onSurface; g.drawString(text, 42, 68 + i * 29) }
    }
}

private class StatusBarWidget : Widget() {
    var message = "준비"
    override fun onPaint(g: Graphics2D, localRegion: Rectangle) {
        g.color = theme.primary; g.fillRect(0, 0, bounds.width, bounds.height)
        g.font = theme.labelFont; g.color = theme.onPrimary; g.drawString(message.take(90), 12, 17)
        val right = "UTF-8     한국어"; g.drawString(right, bounds.width - g.fontMetrics.stringWidth(right) - 14, 17)
    }
}

private fun Widget.paintPanel(g: Graphics2D, title: String) {
    g.color = theme.surface; g.fillRect(0, 0, bounds.width, bounds.height)
    g.color = theme.outline; g.drawRect(0, 0, bounds.width - 1, bounds.height - 1)
    g.font = theme.labelFont.deriveFont(13f); g.color = theme.onSurface; g.drawString(title, 14, 25); g.drawLine(0, 38, bounds.width, 38)
}

private fun blend(base: Color, overlay: Color, alpha: Float): Color {
    val inverse = 1f - alpha
    return Color((base.red * inverse + overlay.red * alpha).toInt(), (base.green * inverse + overlay.green * alpha).toInt(), (base.blue * inverse + overlay.blue * alpha).toInt())
}
