package io.github.nd2026.edited.ui

import io.github.nd2026.edited.AppInfo
import io.github.nd2026.edited.ai.LlmClient
import io.github.nd2026.edited.ai.LlmConfig
import io.github.nd2026.edited.ai.ProseAssistant
import io.github.nd2026.edited.core.Inspection
import io.github.nd2026.edited.core.Parallel
import io.github.nd2026.edited.core.TextArea
import io.github.nd2026.edited.core.TextAreaListener
import io.github.nd2026.edited.core.TextEdit
import io.github.nd2026.edited.event.InputEvent
import io.github.nd2026.edited.export.ChapterText
import io.github.nd2026.edited.export.EpubExporter
import io.github.nd2026.edited.export.TxtExporter
import io.github.nd2026.edited.spell.Proofreader
import io.github.nd2026.edited.project.NovelFormat
import io.github.nd2026.edited.project.Project
import io.github.nd2026.edited.project.ProjectFiles
import io.github.nd2026.edited.project.ProjectNode
import io.github.nd2026.edited.project.ProjectService
import io.github.nd2026.edited.theme.MaterialSpaceTheme
import io.github.nd2026.edited.theme.MaterialLightTheme
import io.github.nd2026.edited.lexer.DefaultMorphemeAnalyzer
import io.github.nd2026.edited.lexer.Morpheme
import io.github.nd2026.edited.lexer.MorphemeAnalyzer
import io.github.nd2026.edited.lexer.Morphology
import io.github.nd2026.edited.ui.components.ButtonWidget
import io.github.nd2026.edited.ui.components.ContextMenuItem
import io.github.nd2026.edited.ui.components.ContextMenuWidget
import io.github.nd2026.edited.ui.components.DialogWidget
import io.github.nd2026.edited.ui.components.PressFeedback
import io.github.nd2026.edited.ui.components.TabBarWidget
import io.github.nd2026.edited.ui.components.TabItem
import io.github.nd2026.edited.ui.panes.CharactersPane
import io.github.nd2026.edited.ui.panes.PaneStyle
import io.github.nd2026.edited.ui.panes.HistoryPane
import io.github.nd2026.edited.ui.panes.ProblemsPane
import io.github.nd2026.edited.ui.panes.SearchPane
import io.github.nd2026.edited.ui.panes.StatsPane
import io.github.nd2026.edited.vcs.HistoryService
import io.github.nd2026.edited.world.CharacterRepository
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Desktop
import java.awt.Font
import java.awt.font.FontRenderContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.event.MouseEvent
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import javax.swing.SwingUtilities

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

    /** Saves every open document with unsaved changes. */
    fun saveAll() = documents.values.filter { it.dirty }.forEach { it.save() }

    /** Re-reads every open document from disk, e.g. after a replace-all rewrote the files. */
    fun reloadFromDisk() {
        for (path in documents.keys.toList()) {
            if (Files.isRegularFile(path)) documents[path] = WorkspaceDocument(path) else close(path)
        }
    }

    /**
     * Follows a rename on disk: every open document at [from] (or under it, for a folder) is re-read from
     * its new place under [to], keeping tab order and the active tab. Save before moving the files.
     */
    fun moved(from: Path, to: Path) {
        val source = from.toAbsolutePath().normalize()
        val target = to.toAbsolutePath().normalize()
        fun follow(path: Path) = if (path.startsWith(source)) target.resolve(source.relativize(path)) else path
        val reopened = documents.keys.associateWith(::follow)
        if (reopened.all { (old, new) -> old == new }) return
        val next = LinkedHashMap<Path, WorkspaceDocument>()
        for ((old, new) in reopened) next[new] = if (old == new) documents.getValue(old) else WorkspaceDocument(new)
        documents.clear()
        documents.putAll(next)
        activePath = activePath?.let(::follow)
    }
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
    private val problems = ProblemsPane()
    private val search = SearchPane()
    private val characters = CharactersPane()
    private val stats = StatsPane()
    private val history = HistoryPane()
    /** Left-pane contents in tool-rail order. */
    private val leftPanes: List<Widget> = listOf(tree, search, characters, stats, history)
    private val status = StatusBarWidget()
    private val captionButtons = listOf(
        CaptionButtonWidget(CaptionButtonWidget.Kind.MINIMIZE) { it.minimize() },
        CaptionButtonWidget(CaptionButtonWidget.Kind.MAXIMIZE) { it.toggleMaximize() },
        CaptionButtonWidget(CaptionButtonWidget.Kind.CLOSE) { it.close() },
    )
    private var session: WorkspaceSession? = null
    private val lintExecutor = Executors.newSingleThreadExecutor { Thread(it, "texted-lint").apply { isDaemon = true } }
    private val aiExecutor = Executors.newSingleThreadExecutor { Thread(it, "texted-ai").apply { isDaemon = true } }
    /** Latest [relint] request; older results arriving later are dropped. EDT only. */
    private var lintGeneration = 0
    /** Last lint result per chapter with the text it was computed from. Written by [Parallel] workers during [relint]. */
    private val lintMemo = ConcurrentHashMap<Path, Pair<String, List<Inspection>>>()
    /** Called on the EDT whenever fresh inspections reach the problems pane (tests wait on it). */
    internal var lintListener: (Map<Path, List<Inspection>>) -> Unit = {}
    var onNewProject: () -> Unit = {}
    var onOpenProject: () -> Unit = {}

    /** Set when the window is undecorated: the top bar then draws the caption buttons and moves the window. */
    var windowControls: WindowControls? = null
        set(value) {
            field = value
            topBar.windowControls = value
            captionButtons.forEach { it.controls = value; it.visible = value != null }
            layout(); requestRepaint()
        }

    init {
        (listOf(topBar, newProjectButton, openProjectButton, rail) + leftPanes + listOf(editor, inspector, problems, status) + captionButtons).forEach(::addChild)
        captionButtons.forEach { it.visible = false }
        newProjectButton.onClick = { onNewProject() }
        openProjectButton.onClick = { onOpenProject() }
        tree.onOpenFile = ::openFile
        tree.onContextMenu = ::showFileMenu
        rail.onSelect = ::showLeftPane
        showLeftPane(0)
        problems.onNavigate = { path, line -> openFile(path); editor.reveal(line) }
        search.onOpenHit = { hit -> openFile(hit.path); editor.reveal(hit.line, hit.column) }
        search.beforeReplace = { session?.saveAll() }
        search.onReplaced = { changed ->
            session?.reloadFromDisk()
            editor.refresh()
            afterFilesChanged()
            status.message = "✓ ${changed}개 파일에서 바꿨습니다"
            status.requestRepaint()
        }
        editor.onActiveChanged = { history.activePath = it; tree.selectedPath = it }
        topBar.onMenuPressed = { index -> if (index == openMenuIndex) closeMenu() else openMenu(index) }
        topBar.onMenuHovered = { index -> if (openMenuIndex >= 0 && index != openMenuIndex) openMenu(index) }
        initialSession?.let { bind(it, emptyList()) } ?: showWelcome()
    }

    fun openProject(project: Project, warnings: List<String> = emptyList()) = bind(WorkspaceSession(project), warnings)

    private var menuLayer: MenuLayer? = null
    private val openMenuIndex get() = topBar.openMenu

    /** Opens menu-bar dropdown [index] (0 파일 … 6 도움말) under its title, closing any other. */
    fun openMenu(index: Int) {
        val overlay = parent as? OverlayHostWidget ?: return
        closeMenu()
        val items = menuItems(index)
        lateinit var layer: MenuLayer
        val menu = ContextMenuWidget(items) { if (menuLayer === layer) closeMenu() }
        layer = MenuLayer(
            menu,
            passThrough = { x, y -> topBar.menuAt(x, y) >= 0 },
            onOutsidePress = ::closeMenu,
            onStep = { step -> openMenu((index + step).mod(topBar.menus.size)) },
        )
        layer.setBounds(overlay.bounds.x, overlay.bounds.y, overlay.bounds.width, overlay.bounds.height)
        val width = menuWidth(items)
        val left = topBar.menuX(index).coerceAtMost(bounds.x + bounds.width - width - 4)
        menu.setBounds(left, bounds.y + TopBarWidget.MENU_HEIGHT, width, ContextMenuWidget.preferredHeight(items.size))
        menuLayer = layer
        topBar.openMenu = index
        overlay.showOverlay(layer)
        hostPane?.focusManager?.requestFocus(menu)
    }

    fun closeMenu() {
        val layer = menuLayer ?: return
        menuLayer = null
        topBar.openMenu = -1
        layer.menu.closeSubmenu()
        (parent as? OverlayHostWidget)?.removeOverlay(layer)
    }

    private fun menuWidth(items: List<ContextMenuItem>): Int {
        val font = theme.labelFont
        val widest = items.maxOfOrNull { font.getStringBounds(it.label, MENU_FRC).width + font.getStringBounds(it.shortcut, MENU_FRC).width } ?: 0.0
        return (widest.toInt() + 72).coerceAtLeast(240)
    }

    private fun menuItems(index: Int): List<ContextMenuItem> {
        val hasProject = session != null
        val text = editor.activeTextArea
        val hasDocument = text != null
        val manyTabs = (session?.openDocuments?.size ?: 0) > 1
        val hasProblems = problems.inspections.values.any { it.isNotEmpty() }
        return when (index) {
            0 -> listOf(
                ContextMenuItem("새 프로젝트…", "Ctrl+N") { onNewProject() },
                ContextMenuItem("폴더 열기…", "Ctrl+O") { onOpenProject() },
                ContextMenuItem.submenu("새로 만들기", newItems(session?.project?.root), enabled = hasProject),
                ContextMenuItem("저장", "Ctrl+S", hasDocument) { saveActiveDocument() },
                ContextMenuItem("탭 닫기", "Ctrl+F4", hasDocument) { closeActiveTab() },
                ContextMenuItem("내보내기 (TXT·EPUB)", "Ctrl+Shift+E", hasProject) { exportManuscript() },
                ContextMenuItem("회차 본문 복사", "Ctrl+Shift+C", hasDocument) { copyActiveChapter() },
                ContextMenuItem("끝내기", "Alt+F4", windowControls != null) { windowControls?.close() },
            )
            1 -> listOf(
                ContextMenuItem("실행 취소", "Ctrl+Z", hasDocument) { text?.undo() },
                ContextMenuItem("다시 실행", "Ctrl+Y", hasDocument) { text?.redo() },
                ContextMenuItem("잘라내기", "Ctrl+X", hasDocument) { text?.cutSelection() },
                ContextMenuItem("복사", "Ctrl+C", hasDocument) { text?.copySelection() },
                ContextMenuItem("붙여넣기", "Ctrl+V", hasDocument) { text?.pasteClipboard() },
                ContextMenuItem("모두 선택", "Ctrl+A", hasDocument) { text?.selectAll() },
                ContextMenuItem("찾기·바꾸기", "Ctrl+Shift+F", hasProject) { showSearch() },
            )
            2 -> PANE_NAMES.mapIndexed { i, name ->
                ContextMenuItem((if (rail.selectedIndex == i) "✓  " else "     ") + name, "Alt+${i + 1}") { showLeftPane(i) }
            } + ContextMenuItem(if (theme.isDark) "     밝은 테마로 전환" else "     어두운 테마로 전환", "") { toggleTheme() } +
                ContextMenuItem((if (editor.revealDirectives) "✓  " else "     ") + "지시문(@) 모두 펼치기", "") { editor.revealDirectives = !editor.revealDirectives }
            3 -> listOf(
                ContextMenuItem("다음 탭", "Alt+→", manyTabs) { selectTab(1) },
                ContextMenuItem("이전 탭", "Alt+←", manyTabs) { selectTab(-1) },
                ContextMenuItem("다음 문제", "F2", hasProblems) { gotoProblem(1) },
                ContextMenuItem("이전 문제", "Shift+F2", hasProblems) { gotoProblem(-1) },
            )
            4 -> listOf(
                ContextMenuItem("AI로 문장 다듬기", "Ctrl+Shift+R", hasDocument) { polishSelection() },
                ContextMenuItem("원고 검수 다시 하기", "", hasProject) { recheck() },
                ContextMenuItem("분량 통계", "Alt+4", hasProject) { showLeftPane(3) },
                ContextMenuItem("설정집", "Alt+3", hasProject) { showLeftPane(2) },
                ContextMenuItem("내보내기 폴더 열기", "", hasProject) { openExportFolder() },
            )
            5 -> listOf(
                ContextMenuItem("지금 기록 남기기", "Ctrl+K", hasProject) { commitNow() },
                ContextMenuItem("원고 기록 보기", "Alt+5", hasProject) { showLeftPane(4) },
            )
            else -> listOf(
                ContextMenuItem("단축키", "") { showTextDialog("단축키", SHORTCUTS) },
                ContextMenuItem("${AppInfo.NAME} 정보", "") { showAbout() },
            )
        }
    }

    /** Closes the active editor tab. */
    fun closeActiveTab() {
        val current = session ?: return
        current.activePath?.let(current::close)
        editor.refresh()
    }

    /** Activates the tab [step] places after (negative: before) the active one, wrapping around. */
    fun selectTab(step: Int) {
        val current = session ?: return
        val paths = current.openDocuments.map { it.path }
        if (paths.size < 2) return
        val index = paths.indexOf(current.activePath)
        current.select(paths[(index + step).mod(paths.size)])
        editor.refresh()
    }

    /** Moves to the next (negative [step]: previous) problem after the caret, across chapters. */
    fun gotoProblem(step: Int) {
        val all = problems.inspections.entries.sortedBy { it.key.toString() }
            .flatMap { (path, list) -> list.map { path to it.line }.distinct().sortedBy { it.second } }
        if (all.isEmpty()) { setStatus("발견된 문제가 없습니다"); return }
        val activePath = session?.activePath?.toAbsolutePath()?.normalize()
        val caretLine = editor.activeTextArea?.textArea?.let { it.lineOf(it.caret) } ?: -1
        // Positions are ordered, so "after the caret" is the first entry past (activePath, caretLine).
        val paths = all.map { it.first }.distinct()
        fun key(path: Path?, line: Int) = paths.indexOf(path).toLong() * Int.MAX_VALUE + line
        val here = key(activePath, caretLine)
        val target = if (step > 0) all.firstOrNull { key(it.first, it.second) > here } ?: all.first()
        else all.lastOrNull { key(it.first, it.second) < here } ?: all.last()
        openFile(target.first)
        editor.reveal(target.second)
        val message = problems.inspections[target.first]?.firstOrNull { it.line == target.second }?.message
        setStatus("${target.first.fileName}:${target.second + 1}  ${message.orEmpty()}")
    }

    /** Saves open documents and records every change in the manuscript history. */
    fun commitNow() {
        val current = session ?: return
        current.saveAll()
        editor.refresh()
        val time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
        val commit = runCatching { HistoryService(current.project.root).commitAll("기록: $time") }
        setStatus(commit.fold({ if (it != null) "✓ 기록을 남겼습니다" else "바뀐 내용이 없습니다" }, { "⚠ 기록 실패: ${it.message}" }))
        afterFilesChanged()
    }

    fun toggleTheme() {
        val pane = hostPane ?: return
        pane.themeProvider.current = if (pane.themeProvider.current.isDark) MaterialLightTheme else MaterialSpaceTheme
        pane.scheduler.requestRepaint(Rectangle(0, 0, pane.width, pane.height))
    }

    private fun recheck() {
        session?.saveAll()
        afterFilesChanged()
        editor.refresh()
        setStatus("✓ 검수·분량·설정집을 새로 읽었습니다")
    }

    private fun openExportFolder() {
        val dir = session?.project?.root?.resolve("export") ?: return
        setStatus(runCatching {
            Files.createDirectories(dir)
            Desktop.getDesktop().open(dir.toFile())
            "✓ $dir"
        }.getOrElse { "⚠ 폴더를 열 수 없습니다: ${it.message}" })
    }

    /** 도움말 › Texted 정보, a winver-style About box. */
    fun showAbout() {
        val overlay = parent as? OverlayHostWidget ?: return
        lateinit var dialog: DialogWidget
        val ok = ButtonWidget("확인", ButtonWidget.Style.FILLED) { dialog.close() }
        dialog = DialogWidget("${AppInfo.NAME} 정보", AboutWidget(), AboutWidget.HEIGHT, listOf(ok), cardWidth = 580)
        dialog.show(overlay)
        hostPane?.focusManager?.requestFocus(ok)
    }

    private fun showTextDialog(title: String, lines: List<String>) {
        val overlay = parent as? OverlayHostWidget ?: return
        lateinit var dialog: DialogWidget
        val ok = ButtonWidget("확인", ButtonWidget.Style.FILLED) { dialog.close() }
        dialog = DialogWidget(title, TextLinesWidget(lines), TextLinesWidget.heightFor(lines), listOf(ok), cardWidth = 560)
        dialog.show(overlay)
        hostPane?.focusManager?.requestFocus(ok)
    }

    private fun setStatus(message: String) {
        status.message = message
        status.requestRepaint()
    }

    fun saveActiveDocument() {
        val saved = runCatching { session?.saveActive() == true }.getOrDefault(false)
        status.message = if (saved) "✓ 저장됨" else "저장할 문서가 없습니다"
        if (saved) {
            val name = session?.activePath?.fileName
            val recorded = session?.let { runCatching { HistoryService(it.project.root).commitAll("저장: $name") }.getOrNull() }
            if (recorded != null) status.message = "✓ 저장됨 · 기록 남김"
            afterFilesChanged()
        }
        editor.refresh()
        status.requestRepaint()
    }

    /** Shows the left pane at [index] of the tool rail (0 원고, 1 찾기, 2 설정, 3 분량, 4 기록). */
    fun showLeftPane(index: Int) {
        leftPanes.forEachIndexed { i, pane -> pane.visible = i == index }
        rail.selectedIndex = index
        requestRepaint()
    }

    /** Opens the find pane, prefilled with the editor's selection if there is one. */
    fun showSearch() {
        showLeftPane(1)
        session?.activeDocument?.textArea?.let { area ->
            area.selectionRange()?.takeUnless(IntRange::isEmpty)?.let { search.searchFor(area.textInRange(it.first, it.last + 1)) }
        }
    }

    /** Writes `export/<title>.txt` and `.epub` into the project folder. */
    fun exportManuscript() {
        val current = session ?: return
        current.saveAll()
        val project = current.project
        val dir = project.root.resolve("export")
        val name = ProjectService.folderNameFor(project.title)
        status.message = runCatching {
            TxtExporter.export(project, dir.resolve("$name.txt"))
            EpubExporter.export(project, dir.resolve("$name.epub"))
            "✓ 내보냄: export/$name.txt, export/$name.epub"
        }.getOrElse { "⚠ 내보내기 실패: ${it.message}" }
        editor.refresh()
        tree.project = project
        status.requestRepaint()
    }

    /**
     * Sends the selection (or the caret's line) to the configured LLM ([LlmConfig]) and replaces it with
     * the polished text as one undoable edit - unless the author changed that text while waiting.
     */
    fun polishSelection() {
        val document = session?.activeDocument ?: return setStatus("다듬을 원고가 없습니다")
        val config = LlmConfig.load()
            ?: return setStatus("⚠ AI 설정이 없습니다: OPENAI_BASE_URL·OPENAI_API_KEY 또는 ${LlmConfig.userFile}")
        val area = document.textArea
        val (start, end) = area.selectionRange()?.takeUnless(IntRange::isEmpty)?.let { it.first to it.last + 1 }
            ?: area.lineOf(area.caret).let { area.lineStart(it) to area.lineEnd(it) }
        val original = area.textInRange(start, end)
        if (original.isBlank()) return setStatus("다듬을 문장을 고르거나 문장이 있는 줄에 커서를 두세요")
        setStatus("AI가 다듬는 중… (${config.model})")
        aiExecutor.execute {
            val result = runCatching { ProseAssistant(LlmClient(config)).polish(original) }
            SwingUtilities.invokeLater {
                result.fold(
                    { polished ->
                        if (end <= area.length && area.textInRange(start, end) == original) {
                            area.replace(start, end, polished)
                            editor.refresh()
                            setStatus("✓ AI가 다듬었습니다 · Ctrl+Z로 되돌리기")
                        } else {
                            setStatus("⚠ 기다리는 동안 원고가 바뀌어 적용하지 않았습니다")
                        }
                    },
                    { setStatus("⚠ AI 다듬기 실패: ${it.message}") },
                )
            }
        }
    }

    /** Copies the open chapter as plain text, ready to paste into a serial platform's editor. */
    fun copyActiveChapter() {
        val document = session?.activeDocument
        status.message = when {
            document == null -> "복사할 원고가 없습니다"
            ClipboardService.writeText(ChapterText.plain(document.textArea.snapshot())) -> "✓ ${document.path.fileName} 본문을 복사했습니다"
            else -> "⚠ 클립보드를 사용할 수 없습니다"
        }
        status.requestRepaint()
    }

    /** Refreshes everything derived from the files on disk: problems, statistics, settings and history. */
    private fun afterFilesChanged() {
        val project = session?.project ?: return
        // Character names and aliases become Kiwi proper nouns, so every morpheme feature sees them as names.
        DefaultMorphemeAnalyzer.setProjectWords(
            runCatching { CharacterRepository.load(project).flatMap { listOf(it.name) + it.aliases } }.getOrDefault(emptyList()),
        )
        relint(project)
        stats.refresh()
        characters.refresh()
        history.refresh()
        search.search()
    }

    /**
     * Lints every chapter from [lintExecutor] - the first run may wait for Kiwi to load (or download) - and
     * publishes the result on the EDT unless a newer run superseded it. Unchanged chapters reuse [lintMemo].
     * Chapters are read and linted concurrently on [Parallel.pool], and each chapter's morpheme analysis
     * forks its lines onto the same workers, so a whole-project relint keeps every core busy.
     */
    private fun relint(project: Project) {
        val generation = ++lintGeneration
        val chapters = project.chapters().map { it.path.toAbsolutePath().normalize() }
        lintExecutor.execute {
            val result = runCatching {
                val inspections = Parallel.map(chapters) { path ->
                    val text = Files.readString(path)
                    lintMemo[path]?.takeIf { it.first == text }?.second ?: Proofreader.run(text).also { lintMemo[path] = text to it }
                }
                chapters.zip(inspections).toMap()
            }.getOrDefault(emptyMap())
            lintMemo.keys.retainAll(chapters.toSet())
            SwingUtilities.invokeLater {
                if (generation != lintGeneration) return@invokeLater
                problems.inspections = result
                lintListener(result)
            }
        }
    }

    private fun bind(next: WorkspaceSession, warnings: List<String>) {
        session = next
        Morphology.warmUp()
        topBar.projectTitle = next.project.title
        tree.project = next.project
        editor.session = next
        inspector.project = next.project
        problems.project = next.project
        search.project = next.project
        characters.project = next.project
        stats.project = next.project
        history.project = next.project
        history.activePath = next.activePath
        afterFilesChanged()
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

    /** "새로 만들기" rows: one per [NovelFormat] plus a folder, created next to [target] (see [ProjectFiles.folderFor]). */
    private fun newItems(target: Path?): List<ContextMenuItem> {
        val enabled = session != null && target != null
        return NovelFormat.entries.map { format -> ContextMenuItem(format.label, ".${format.extension}", enabled) { askNewDocument(format, target!!) } } +
            ContextMenuItem("폴더", "", enabled) { askNewFolder(target!!) }
    }

    /** Right-click menu of a project-tree row: 새로 만들기 ▸, 이름 바꾸기, 확장자 바꾸기 ▸. */
    private fun showFileMenu(node: ProjectNode, x: Int, y: Int) {
        val project = session?.project ?: return
        val overlay = parent as? OverlayHostWidget ?: return
        val isRoot = node.path.toAbsolutePath().normalize() == project.root.toAbsolutePath().normalize()
        val items = buildList {
            add(ContextMenuItem.submenu("새로 만들기", newItems(node.path)))
            add(ContextMenuItem("이름 바꾸기…", "", !isRoot) { askRename(node.path) })
            if (node is ProjectNode.File) add(ContextMenuItem.submenu("확장자 바꾸기", extensionItems(node.path)))
        }
        lateinit var menu: ContextMenuWidget
        menu = ContextMenuWidget(items) { overlay.removeOverlay(menu) }
        val width = ContextMenuWidget.preferredWidth(items, theme.labelFont)
        val height = ContextMenuWidget.preferredHeight(items.size)
        val ob = overlay.bounds
        menu.setBounds(
            x.coerceIn(ob.x + 4, (ob.x + ob.width - width - 4).coerceAtLeast(ob.x + 4)),
            y.coerceIn(ob.y + 4, (ob.y + ob.height - height - 4).coerceAtLeast(ob.y + 4)),
            width,
            height,
        )
        overlay.showOverlay(menu, dismissOnOutside = true)
        hostPane?.focusManager?.requestFocus(menu)
    }

    /** "확장자 바꾸기" rows: Texted's formats, then the older text formats; the current one is checked. */
    private fun extensionItems(path: Path): List<ContextMenuItem> {
        val current = path.fileName.toString().substringAfterLast('.', "").lowercase()
        val choices = NovelFormat.entries.map { it.extension to it.label } + NovelFormat.LEGACY_EXTENSIONS
        return choices.map { (extension, label) ->
            ContextMenuItem((if (extension == current) "✓  " else "     ") + label, ".$extension", extension != current) {
                renameTo(path) { ProjectFiles.changeExtension(it, path, extension) }?.let { error -> setStatus("⚠ $error") }
            }
        }
    }

    private fun askNewDocument(format: NovelFormat, target: Path) {
        val project = session?.project ?: return
        val dir = ProjectFiles.folderFor(project, format, target)
        val folder = project.root.toAbsolutePath().normalize().relativize(dir).toString().replace('\\', '/')
        showNameDialog(
            title = "새 ${format.label}",
            hint = "$folder/ 에 .${format.extension} 파일로 만듭니다" + if (format == NovelFormat.EPISODE) " · 번호는 자동" else "",
            initial = if (format == NovelFormat.EPISODE) ProjectFiles.nextEpisodeTitle(dir) else "",
            placeholder = when (format) {
                NovelFormat.EPISODE -> "에피소드 제목"
                NovelFormat.CHARACTER -> "인물 이름"
                NovelFormat.WORLD -> "장소·단체·물건 이름"
            },
            action = "만들기",
        ) { name ->
            runCatching { ProjectFiles.create(project, format, dir, name) }.fold({ created ->
                filesChanged()
                openFile(created)
                setStatus("✓ 만들었습니다: ${created.fileName}")
                null
            }, { it.message })
        }
    }

    private fun askNewFolder(target: Path) {
        val project = session?.project ?: return
        val dir = if (Files.isDirectory(target)) target else target.parent
        showNameDialog("새 폴더", "${dir.fileName}/ 안에 만듭니다", "", "폴더 이름", "만들기") { name ->
            runCatching { ProjectFiles.createFolder(project, dir, name) }.fold({ filesChanged(); setStatus("✓ 폴더를 만들었습니다: ${it.fileName}"); null }, { it.message })
        }
    }

    private fun askRename(path: Path) {
        val isFolder = Files.isDirectory(path)
        val extension = if (isFolder) "" else path.fileName.toString().substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        val hint = if (isFolder || extension.isEmpty()) "새 이름" else "확장자($extension)는 그대로 둡니다 · 바꾸려면 '확장자 바꾸기'"
        showNameDialog("이름 바꾸기", hint, ProjectFiles.baseName(path), "새 이름", "바꾸기") { name ->
            renameTo(path) { ProjectFiles.rename(it, path, name + extension) }
        }
    }

    /**
     * Saves open documents, runs the rename [op], moves affected tabs to the new path and refreshes the tree
     * and every pane. Returns the error message, or null on success.
     */
    private fun renameTo(path: Path, op: (Project) -> Path): String? {
        val current = session ?: return null
        current.saveAll()
        return runCatching { op(current.project) }.fold({ moved ->
            current.moved(path, moved)
            editor.refresh()
            filesChanged()
            setStatus("✓ ${path.fileName} → ${moved.fileName}")
            null
        }, { it.message ?: it.toString() })
    }

    /** The tree and every derived pane after files were created or renamed. */
    private fun filesChanged() {
        tree.project = session?.project
        afterFilesChanged()
    }

    private fun showNameDialog(title: String, hint: String, initial: String, placeholder: String, action: String, onSubmit: (String) -> String?) {
        val overlay = parent as? OverlayHostWidget ?: return
        NameDialog(title, hint, initial, placeholder, action, onSubmit).show(overlay)
    }

    private companion object {
        val MENU_FRC = FontRenderContext(null, true, true)
        val PANE_NAMES = listOf("원고", "찾기·바꾸기", "설정집", "분량", "원고 기록")
        val SHORTCUTS = listOf(
            "새 프로젝트 / 폴더 열기\tCtrl+N / Ctrl+O",
            "저장 (기록·검수·분량 갱신)\tCtrl+S",
            "탭 닫기\tCtrl+F4",
            "실행 취소 / 다시 실행\tCtrl+Z / Ctrl+Y",
            "낱말 단위로 이동\tCtrl+← / Ctrl+→",
            "낱말 단위로 지우기\tCtrl+Backspace / Ctrl+Delete",
            "원고 처음 / 끝으로\tCtrl+Home / Ctrl+End",
            "한 화면 위 / 아래\tPageUp / PageDown",
            "찾기·바꾸기\tCtrl+Shift+F",
            "내보내기 (TXT·EPUB)\tCtrl+Shift+E",
            "회차 본문 복사\tCtrl+Shift+C",
            "AI로 문장 다듬기 (선택 또는 지금 줄)\tCtrl+Shift+R",
            "패널 전환 (원고 … 기록)\tAlt+1 … Alt+5",
            "다음 / 이전 탭\tAlt+→ / Alt+←",
            "다음 / 이전 문제\tF2 / Shift+F2",
            "지금 기록 남기기\tCtrl+K",
            "편집기 글꼴 크기\tCtrl+휠",
            "메뉴 열기\tAlt+F, E, V, N, T, G, H",
        )
    }

    override fun layout() {
        val b = bounds
        val topH = 76
        val statusH = 24
        val railW = 52
        val bottomH = (b.height * .23).toInt().coerceIn(140, 190)
        val leftW = if (b.width >= 1050) 280 else 220
        val rightW = if (b.width >= 1120) 260 else 0
        val contentTop = b.y + topH
        val contentBottom = b.y + b.height - statusH
        val mainBottom = contentBottom - bottomH
        val editorLeft = b.x + railW + leftW
        val editorRight = b.x + b.width - rightW
        val captionW = if (windowControls != null) CaptionButtonWidget.WIDTH * captionButtons.size else 0
        topBar.captionWidth = captionW
        topBar.setBounds(b.x, b.y, b.width, topH)
        captionButtons.forEachIndexed { i, button ->
            button.setBounds(b.x + b.width - captionW + i * CaptionButtonWidget.WIDTH, b.y, CaptionButtonWidget.WIDTH, TopBarWidget.MENU_HEIGHT - 1)
        }
        // Centre the buttons in the toolbar row so they stay inside the top bar.
        val buttonY = b.y + TopBarWidget.MENU_HEIGHT + (topH - TopBarWidget.MENU_HEIGHT - ButtonWidget.HEIGHT) / 2
        val newWidth = newProjectButton.measure().width
        newProjectButton.setBounds(b.x + 12, buttonY, newWidth, ButtonWidget.HEIGHT)
        val openWidth = openProjectButton.measure().width
        openProjectButton.setBounds(b.x + 20 + newWidth, buttonY, openWidth, ButtonWidget.HEIGHT)
        topBar.chipX = 20 + newWidth + openWidth + 12
        rail.setBounds(b.x, contentTop, railW, contentBottom - contentTop)
        leftPanes.forEach { it.setBounds(b.x + railW, contentTop, leftW, mainBottom - contentTop) }
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
    val menus = listOf("파일(F)", "편집(E)", "보기(V)", "이동(N)", "도구(T)", "Git(G)", "도움말(H)")
    private val menuFeedback = menus.map { PressFeedback { requestRepaint() } }
    /** Title rectangles in root coordinates, refreshed on every paint. */
    private var menuRects = emptyList<Rectangle>()
    private var hoverMenu = -1

    /** Index of the dropdown currently open, highlighted like a pressed title. */
    var openMenu = -1
        set(value) { field = value; requestRepaint() }
    var onMenuPressed: (Int) -> Unit = {}
    var onMenuHovered: (Int) -> Unit = {}

    var projectTitle = "프로젝트 없음"
        set(value) { field = value; requestRepaint() }

    var windowControls: WindowControls? = null
    /** Width kept free at the right of the menu row for the caption buttons. */
    var captionWidth = 0
    /** Left edge of the project chip, just after the new/open buttons. */
    var chipX = 250
    private var dragX = 0
    private var dragY = 0
    private var dragging = false
    private var lastPress = 0L

    override fun onPaint(g: Graphics2D, localRegion: Rectangle) {
        g.color = theme.surface
        g.fillRect(0, 0, bounds.width, bounds.height)

        // IDEA-style compact menu row.
        g.font = theme.labelFont
        g.color = blend(theme.surface, theme.primary, .12f)
        g.fillRoundRect(10, 5, 22, 22, 6, 6)
        g.color = theme.primary
        g.drawString("T", 17, 21)
        var menuX = 36
        val rects = ArrayList<Rectangle>()
        menus.forEachIndexed { i, menu ->
            val width = g.fontMetrics.stringWidth(menu) + 16
            val feedback = menuFeedback[i]
            feedback.update(i == hoverMenu || i == openMenu, i == openMenu)
            feedback.subtleFill(theme)?.let { g.color = it; g.fillRoundRect(menuX, 3, width, MENU_HEIGHT - 7, PressFeedback.CORNER_ARC, PressFeedback.CORNER_ARC) }
            g.color = theme.onSurface
            g.drawString(menu, menuX + 8, 21)
            rects += Rectangle(bounds.x + menuX, bounds.y + 3, width, MENU_HEIGHT - 7)
            menuX += width + 2
        }
        menuRects = rects
        g.color = theme.outline
        val titleText = "Texted — $projectTitle"
        val titleX = bounds.width - captionWidth - g.fontMetrics.stringWidth(titleText) - 16
        if (titleX > menuX + 24) g.drawString(titleText, titleX, 21)
        g.color = theme.separator
        g.drawLine(0, MENU_HEIGHT - 1, bounds.width, MENU_HEIGHT - 1)

        // Main toolbar: project action, VCS context, global search and run actions.
        val toolbarY = MENU_HEIGHT + (bounds.height - MENU_HEIGHT - 31) / 2
        val actions = "⑂ main    ▶   ◆   ⚙   " + if (theme.isDark) "☀" else "☾"
        val actionsX = bounds.width - g.fontMetrics.stringWidth(actions) - 18
        val projectWidth = 190
        val showChip = bounds.width >= 1100
        if (showChip) paintChip(g, chipX, toolbarY, projectWidth, 31, "▾  $projectTitle")
        // Keep the search box between the chip (or buttons) and the actions so nothing overlaps.
        val searchLeft = if (showChip) chipX + projectWidth + 16 else chipX
        var searchWidth = minOf(330, (bounds.width * .28).toInt())
        val searchX = maxOf((bounds.width - searchWidth) / 2, searchLeft)
        searchWidth = minOf(searchWidth, actionsX - 16 - searchX)
        if (searchWidth >= 120) {
            g.color = blend(theme.surface, theme.primary, .09f)
            g.fillRoundRect(searchX, toolbarY, searchWidth, 31, 8, 8)
            g.color = theme.outline
            g.drawRoundRect(searchX, toolbarY, searchWidth, 31, 8, 8)
            val clip = g.clip
            g.clipRect(searchX, toolbarY, searchWidth - 8, 31)
            g.drawString("⌕  전체 검색    Shift+Shift", searchX + 12, toolbarY + 20)
            g.clip = clip
        }

        g.color = theme.onSurface
        g.drawString(actions, actionsX, toolbarY + 20)
        g.color = theme.separator
        g.drawLine(0, bounds.height - 1, bounds.width, bounds.height - 1)
    }

    override fun onMouseEvent(event: InputEvent.MouseInput): Boolean {
        val localY = event.y - bounds.y
        val controls = windowControls
        when (event) {
            is InputEvent.MousePressed -> {
                val menu = menuAt(event.x, event.y)
                if (menu >= 0 && event.button == 1) { onMenuPressed(menu); return true }
                if (localY < MENU_HEIGHT && controls != null && event.button == 1) {
                    // The menu row is the window's title bar: drag to move, double-click to maximize.
                    val now = System.currentTimeMillis()
                    if (now - lastPress < 400) { lastPress = 0; controls.toggleMaximize(); return true }
                    lastPress = now
                    dragX = event.x; dragY = event.y; dragging = !controls.maximized
                    return true
                }
                if (localY < MENU_HEIGHT || event.x < bounds.x + bounds.width - 54) return false
                val pane = hostPane ?: return false
                pane.themeProvider.current = if (pane.themeProvider.current.isDark) MaterialLightTheme else MaterialSpaceTheme
                pane.scheduler.requestRepaint(Rectangle(0, 0, pane.width, pane.height))
                return true
            }
            // Coordinates are window-relative, so after a move the press point is back under the cursor.
            is InputEvent.MouseDragged -> if (dragging) { controls?.moveBy(event.x - dragX, event.y - dragY); return true }
            is InputEvent.MouseReleased, is InputEvent.MouseCancelled -> dragging = false
            is InputEvent.MouseMoved, is InputEvent.MouseEntered -> {
                val menu = menuAt(event.x, event.y)
                if (menu != hoverMenu) { hoverMenu = menu; requestRepaint(); if (menu >= 0) onMenuHovered(menu) }
            }
            is InputEvent.MouseExited -> if (hoverMenu >= 0) { hoverMenu = -1; requestRepaint() }
            else -> {}
        }
        return false
    }

    /** The menu title under root point ([x], [y]), or -1. */
    fun menuAt(x: Int, y: Int): Int = menuRects.indexOfFirst { it.contains(x, y) }

    /** Root-relative left edge of menu [index]'s title, where its dropdown opens. */
    fun menuX(index: Int): Int = menuRects.getOrNull(index)?.x ?: (bounds.x + 36)

    companion object {
        /** Height of the menu row, which doubles as the window title bar. */
        const val MENU_HEIGHT = 30
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
    private val labels = listOf("원고", "찾기", "설정", "분량", "기록")
    private val feedback = labels.map { PressFeedback { requestRepaint() } }
    private var hoverIndex = -1
    private var pressedIndex = -1
    var selectedIndex = 0
        set(value) { field = value; requestRepaint() }
    var onSelect: (Int) -> Unit = {}
    override fun onPaint(g: Graphics2D, localRegion: Rectangle) {
        g.color = blend(theme.surface, theme.primary, .035f); g.fillRect(0, 0, bounds.width, bounds.height)
        g.color = theme.separator; g.drawLine(bounds.width - 1, 0, bounds.width - 1, bounds.height)
        g.font = theme.labelFont.deriveFont(12f)
        labels.forEachIndexed { i, text ->
            val y = 14 + i * 48
            val item = feedback[i]
            item.update(i == hoverIndex, i == pressedIndex)
            if (i == selectedIndex) { g.color = blend(theme.surface, theme.primary, .18f); g.fillRoundRect(8, y, 36, 36, 18, 18) }
            item.subtleFill(theme)?.let { g.color = it; g.fillRoundRect(8, y, 36, 36, 18, 18) }
            g.color = item.content(if (i == selectedIndex) theme.primary else theme.outline)
            g.drawString(text, 26 - g.fontMetrics.stringWidth(text) / 2, y + 23)
        }
    }
    private fun indexAt(y: Int): Int = ((y - bounds.y - 14) / 48).takeIf { y - bounds.y >= 14 && it in labels.indices && (y - bounds.y - 14) % 48 < 36 } ?: -1
    override fun onMouseEvent(event: InputEvent.MouseInput): Boolean {
        when (event) {
            is InputEvent.MousePressed -> {
                val index = indexAt(event.y)
                if (index < 0) return false
                pressedIndex = index; onSelect(index); requestRepaint(); return true
            }
            is InputEvent.MouseReleased, is InputEvent.MouseCancelled -> { pressedIndex = -1; hoverIndex = if (bounds.contains(event.x, event.y)) indexAt(event.y) else -1; requestRepaint(); return true }
            is InputEvent.MouseMoved, is InputEvent.MouseEntered -> indexAt(event.y).let { if (it != hoverIndex) { hoverIndex = it; requestRepaint() } }
            is InputEvent.MouseExited -> { hoverIndex = -1; requestRepaint() }
            else -> {}
        }
        return false
    }
}

private data class TreeRow(val node: ProjectNode, val depth: Int)

/**
 * IntelliJ-style project view: the project root first, folders before files, chevrons that
 * expand/collapse folders, file-type icons, indent guides, a full-width selection that follows
 * the active editor tab, and wheel scrolling.
 */
private class ProjectTreeWidget : Widget() {
    var project: Project? = null
        set(value) {
            val reopened = field?.root != value?.root
            field = value
            root = value?.tree()
            if (reopened) {
                expanded.clear()
                root?.let { r -> expanded.add(r.path); r.children.filterIsInstance<ProjectNode.Directory>().forEach { expanded.add(it.path) } }
                scrollRow = 0
            }
            rebuild()
        }
    var onOpenFile: (Path) -> Unit = {}
    /** Right-click on a row (or below the rows, for the root) at root-relative x, y. */
    var onContextMenu: (ProjectNode, Int, Int) -> Unit = { _, _, _ -> }

    /** The file shown in the active editor tab; its row is highlighted like IntelliJ's "always select opened file". */
    var selectedPath: Path? = null
        set(value) { field = value?.toAbsolutePath()?.normalize(); requestRepaint() }

    private var root: ProjectNode.Directory? = null
    private val expanded = HashSet<Path>()
    private var rows = emptyList<TreeRow>()
    private var scrollRow = 0
    private val feedback = HashMap<Path, PressFeedback>()
    private var hoverRow = -1
    private var pressedRow = -1
    private var lastClickPath: Path? = null
    private var lastClickTime = 0L

    private fun toggle(path: Path) {
        if (!expanded.remove(path)) expanded.add(path)
        rebuild()
    }

    private fun rebuild() {
        val r = root
        rows = if (r == null) emptyList() else buildList {
            add(TreeRow(r, 0))
            if (r.path in expanded) addChildren(r, 1)
        }
        feedback.keys.retainAll(rows.map { it.node.path }.toSet())
        scrollRow = scrollRow.coerceIn(0, (rows.size - 1).coerceAtLeast(0))
        requestRepaint()
    }

    private fun MutableList<TreeRow>.addChildren(directory: ProjectNode.Directory, depth: Int) {
        val sorted = directory.children.sortedWith(compareBy<ProjectNode>({ it !is ProjectNode.Directory }, { it.name.lowercase() }))
        for (child in sorted) {
            add(TreeRow(child, depth))
            if (child is ProjectNode.Directory && child.path in expanded) addChildren(child, depth + 1)
        }
    }

    private val visibleRows get() = ((bounds.height - LIST_TOP) / ROW_HEIGHT).coerceAtLeast(0)

    override fun onPaint(g: Graphics2D, localRegion: Rectangle) {
        paintPanel(g, "Project")
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        val clip = g.clip
        g.clipRect(0, LIST_TOP, bounds.width, bounds.height - LIST_TOP)
        g.font = theme.labelFont.deriveFont(13f)
        val fm = g.fontMetrics
        val last = (scrollRow + visibleRows + 1).coerceAtMost(rows.size)
        for (index in scrollRow until last) {
            val row = rows[index]
            val top = LIST_TOP + (index - scrollRow) * ROW_HEIGHT
            val node = row.node
            val item = feedback.getOrPut(node.path) { PressFeedback { requestRepaint() } }
            item.update(index == hoverRow, index == pressedRow)
            val selected = node.path.toAbsolutePath().normalize() == selectedPath
            if (selected) {
                g.color = blend(theme.surface, theme.primary, if (theme.isDark) .22f else .16f)
                g.fillRect(0, top, bounds.width, ROW_HEIGHT)
            }
            item.subtleFill(theme)?.let { g.color = it; g.fillRect(0, top, bounds.width, ROW_HEIGHT) }

            // Indent guides, one per ancestor level below the root.
            g.color = theme.separator
            for (level in 1 until row.depth) {
                val x = indentOf(level) + CHEVRON / 2
                g.drawLine(x, top, x, top + ROW_HEIGHT)
            }

            val x = indentOf(row.depth)
            val midY = top + ROW_HEIGHT / 2
            if (node is ProjectNode.Directory) paintChevron(g, x, midY, node.path in expanded, item.content(theme.outline))
            val iconX = x + CHEVRON + 2
            paintIcon(g, node, iconX, midY - ICON / 2, row.depth == 0)

            val textX = iconX + ICON + 6
            val baseline = top + (ROW_HEIGHT + fm.ascent - fm.descent) / 2
            val name = if (row.depth == 0) project?.title ?: node.name else node.name
            g.color = item.content(if (node.name.startsWith(".")) theme.outline else theme.onSurface)
            g.font = if (row.depth == 0) theme.labelFont.deriveFont(Font.BOLD, 13f) else theme.labelFont.deriveFont(13f)
            val label = PaneStyle.ellipsize(name, g.fontMetrics, bounds.width - textX - 8)
            g.drawString(label, textX, baseline)
            if (row.depth == 0) {
                // IntelliJ shows the project's location after the root's name.
                val pathX = textX + g.fontMetrics.stringWidth(label) + 8
                g.font = theme.labelFont.deriveFont(12f)
                g.color = theme.outline
                g.drawString(PaneStyle.ellipsize(node.path.toString(), g.fontMetrics, bounds.width - pathX - 8), pathX, baseline)
            }
        }
        g.clip = clip
    }

    private fun indentOf(depth: Int) = 8 + depth * INDENT

    private fun paintChevron(g: Graphics2D, x: Int, midY: Int, open: Boolean, color: Color) {
        g.color = color
        g.stroke = BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        val cx = x + CHEVRON / 2
        if (open) {
            g.drawLine(cx - 4, midY - 2, cx, midY + 2)
            g.drawLine(cx, midY + 2, cx + 4, midY - 2)
        } else {
            g.drawLine(cx - 2, midY - 4, cx + 2, midY)
            g.drawLine(cx + 2, midY, cx - 2, midY + 4)
        }
    }

    private fun paintIcon(g: Graphics2D, node: ProjectNode, x: Int, y: Int, isRoot: Boolean) {
        g.stroke = BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        if (node is ProjectNode.Directory) {
            val color = when {
                isRoot -> theme.primary
                node.name == "manuscript" -> Color(0x82, 0xAA, 0xFF)
                node.name == "characters" -> Color(0xC7, 0x92, 0xEA)
                node.name == "world" -> Color(0xC3, 0xE8, 0x8D)
                node.name == "export" -> Color(0xF7, 0x8C, 0x6C)
                else -> Color(0x95, 0x9D, 0xAA)
            }
            // Folder: a tab on the top-left over a rounded body.
            g.color = color
            g.fillRoundRect(x + 1, y + 2, 6, 4, 2, 2)
            g.fillRoundRect(x + 1, y + 4, ICON - 2, ICON - 6, 3, 3)
            return
        }
        val extension = node.name.substringAfterLast('.', "").lowercase()
        val (color, badge) = when {
            extension == "episode" -> Color(0x82, 0xAA, 0xFF) to "Ep"
            extension == "character" -> Color(0xC7, 0x92, 0xEA) to "C"
            extension == "world" -> Color(0xC3, 0xE8, 0x8D) to "W"
            extension == "md" -> Color(0x82, 0xAA, 0xFF) to "M"
            extension == "txt" -> Color(0x95, 0x9D, 0xAA) to "T"
            extension == "json" -> Color(0xFF, 0xCB, 0x6B) to "{}"
            extension == "epub" -> Color(0xC3, 0xE8, 0x8D) to "E"
            node.name.startsWith(".git") -> Color(0xF0, 0x71, 0x78) to "G"
            else -> Color(0x95, 0x9D, 0xAA) to ""
        }
        // Page with a folded corner, the type letter inside.
        val w = ICON - 4
        val left = x + 2
        g.color = color
        g.drawLine(left, y + 1, left + w - 4, y + 1)
        g.drawLine(left + w - 4, y + 1, left + w, y + 5)
        g.drawLine(left + w, y + 5, left + w, y + ICON - 1)
        g.drawLine(left + w, y + ICON - 1, left, y + ICON - 1)
        g.drawLine(left, y + ICON - 1, left, y + 1)
        if (badge.isNotEmpty()) {
            g.font = theme.labelFont.deriveFont(Font.BOLD, if (badge.length > 1) 7f else 8f)
            val fm = g.fontMetrics
            g.drawString(badge, left + (w - fm.stringWidth(badge)) / 2 + 1, y + ICON - 4)
        }
    }

    private fun rowAt(y: Int): Int {
        val localY = y - bounds.y - LIST_TOP
        if (localY < 0) return -1
        return (scrollRow + localY / ROW_HEIGHT).takeIf { it in rows.indices } ?: -1
    }

    override fun onMouseEvent(event: InputEvent.MouseInput): Boolean {
        when (event) {
            is InputEvent.MousePressed -> {
                val index = rowAt(event.y)
                if (event.button == MouseEvent.BUTTON3) {
                    // Right-click: the row's menu, or the project root's below the last row.
                    val target = rows.getOrNull(index)?.node ?: root ?: return false
                    if (index >= 0) selectedPath = target.path
                    onContextMenu(target, event.x, event.y)
                    return true
                }
                val node = rows.getOrNull(index)?.node ?: return false
                pressedRow = index
                val row = rows[index]
                val now = System.currentTimeMillis()
                val doubleClick = node.path == lastClickPath && now - lastClickTime <= DOUBLE_CLICK_MS
                lastClickPath = if (doubleClick) null else node.path
                lastClickTime = now
                val onChevron = node is ProjectNode.Directory && (event.x - bounds.x) in indentOf(row.depth) until indentOf(row.depth) + CHEVRON + 2
                // IntelliJ: click selects, double-click opens a file or toggles a folder; the chevron toggles at once.
                when {
                    onChevron || (doubleClick && node is ProjectNode.Directory) -> toggle(node.path)
                    doubleClick && node is ProjectNode.File -> onOpenFile(node.path)
                }
                selectedPath = node.path
                requestRepaint()
                return true
            }
            is InputEvent.MouseReleased, is InputEvent.MouseCancelled -> {
                pressedRow = -1
                hoverRow = if (bounds.contains(event.x, event.y)) rowAt(event.y) else -1
                requestRepaint()
                return true
            }
            is InputEvent.MouseMoved, is InputEvent.MouseEntered -> rowAt(event.y).let { if (it != hoverRow) { hoverRow = it; requestRepaint() } }
            is InputEvent.MouseExited -> { hoverRow = -1; requestRepaint() }
            is InputEvent.Scroll -> {
                scrollRow = (scrollRow + PaneStyle.scrollRows(event.unitsToScroll) * 3).coerceIn(0, (rows.size - visibleRows).coerceAtLeast(0))
                hoverRow = rowAt(event.y)
                requestRepaint()
                return true
            }
            else -> {}
        }
        return false
    }

    private companion object {
        const val LIST_TOP = 44
        const val ROW_HEIGHT = 24
        const val INDENT = 18
        const val CHEVRON = 14
        const val ICON = 16

        /** The OS double-click interval (Windows setting), 500 ms if unknown. */
        val DOUBLE_CLICK_MS: Int = runCatching { Toolkit.getDefaultToolkit().getDesktopProperty("awt.multiClickInterval") as? Int }.getOrNull() ?: 500
    }
}

private class DocumentEditorWidget : Container() {
    private val tabs = TabBarWidget()
    private var activeEditor: TextAreaWidget? = null
    var session: WorkspaceSession? = null
        set(value) { field = value; refresh() }
    var onActiveChanged: (Path?) -> Unit = {}

    /** The editor of the active tab, for menu commands (undo, clipboard, …). */
    val activeTextArea: TextAreaWidget? get() = activeEditor

    /** Shows directives in full in every tab instead of folding them (see [TextAreaWidget.revealDirectives]). */
    var revealDirectives = false
        set(value) { field = value; activeEditor?.revealDirectives = value }

    /** Editor text size chosen with Ctrl+wheel; null until the author zooms (theme default). */
    private var editorFontSize: Float? = null

    init {
        addChild(tabs)
        tabs.onSelect = { session?.select(Path.of(it)); refresh() }
        tabs.onClose = { session?.close(Path.of(it)); refresh() }
    }

    fun refresh() {
        val current = session
        tabs.tabs = current?.openDocuments?.map { TabItem(it.path.toString(), it.path.fileName.toString() + if (it.dirty) " •" else "") }.orEmpty()
        tabs.selectedId = current?.activePath?.toString()
        val document = current?.activeDocument
        // Keep the editor (scroll position, morpheme cache) when the active document didn't change, e.g. after Ctrl+S.
        if (activeEditor?.textArea !== document?.textArea) {
            activeEditor?.let(::removeChild)
            // Only prose (Texted formats and Markdown) gets Kiwi morpheme analysis; JSON, .gitignore etc. stay plain text.
            activeEditor = document?.let {
                TextAreaWidget(it.textArea, analyzerFor(it.path)).apply {
                    revealDirectives = this@DocumentEditorWidget.revealDirectives
                    // Ctrl+wheel zoom applies to every tab, not just the one it was done in.
                    fontSize = editorFontSize
                    onFontSizeChanged = { size -> editorFontSize = size }
                }
            }
            activeEditor?.let(::addChild)
        }
        layout(); requestRepaint()
        onActiveChanged(current?.activePath)
    }

    /** Moves the caret to [line] (0-based) and [column] of the active document and scrolls it into view. */
    fun reveal(line: Int, column: Int = 0) {
        val widget = activeEditor ?: return
        val area = widget.textArea
        if (area.lineCount == 0) return
        val target = line.coerceIn(0, area.lineCount - 1)
        area.moveCaretTo((area.lineStart(target) + column).coerceAtMost(area.lineEnd(target)))
        widget.revealCaret()
        hostPane?.focusManager?.requestFocus(widget)
    }

    override fun layout() {
        tabs.setBounds(bounds.x, bounds.y, bounds.width, 40)
        activeEditor?.setBounds(bounds.x, bounds.y + 40, bounds.width, (bounds.height - 40).coerceAtLeast(1))
    }
    override fun onPaint(g: Graphics2D, localRegion: Rectangle) {
        g.color = theme.surface; g.fillRect(0, 0, bounds.width, bounds.height)
        g.color = theme.separator; g.drawRect(0, 0, bounds.width - 1, bounds.height - 1)
        g.color = theme.outline
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

private fun blendColor(base: Color, overlay: Color, amount: Float): Color {
    val t = amount.coerceIn(0f, 1f)
    fun mix(a: Int, b: Int) = (a + (b - a) * t).toInt()
    return Color(mix(base.red, overlay.red), mix(base.green, overlay.green), mix(base.blue, overlay.blue), mix(base.alpha, overlay.alpha))
}

/** Native window operations behind the custom title bar of an undecorated frame. */
interface WindowControls {
    val maximized: Boolean
    fun minimize()
    fun toggleMaximize()
    fun close()
    fun moveBy(dx: Int, dy: Int)
}

/** IntelliJ/Windows-style caption button: flat 46px cell, thin glyph, red hover for close. */
private class CaptionButtonWidget(val kind: Kind, val action: (WindowControls) -> Unit) : Widget() {
    enum class Kind { MINIMIZE, MAXIMIZE, CLOSE }

    var controls: WindowControls? = null
    private val feedback = PressFeedback { requestRepaint() }

    override fun onPaint(g: Graphics2D, localRegion: Rectangle) {
        val close = kind == Kind.CLOSE
        feedback.update(hovered, pressed)
        val hover = feedback.hoverAmount
        if (close) {
            // Windows 11 caption close: #C42B1C on hover, 90 % opacity while pressed, white glyph.
            if (hover > .01f) {
                g.color = Color(0xC4, 0x2B, 0x1C, (255 * hover * (1f - .1f * feedback.pressAmount)).toInt())
                g.fillRect(0, 0, bounds.width, bounds.height)
            }
        } else {
            feedback.subtleFill(theme)?.let { g.color = it; g.fillRect(0, 0, bounds.width, bounds.height) }
        }
        g.color = if (close) blendColor(feedback.content(theme.onSurface), Color.WHITE, hover) else feedback.content(theme.onSurface)
        g.stroke = BasicStroke(1f)
        val cx = bounds.width / 2
        val cy = bounds.height / 2
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, if (close) RenderingHints.VALUE_ANTIALIAS_ON else RenderingHints.VALUE_ANTIALIAS_OFF)
        when (kind) {
            Kind.MINIMIZE -> g.drawLine(cx - 5, cy, cx + 5, cy)
            Kind.MAXIMIZE -> if (controls?.maximized == true) {
                g.drawRect(cx - 5, cy - 3, 8, 8)
                g.drawLine(cx - 3, cy - 5, cx + 5, cy - 5)
                g.drawLine(cx + 5, cy - 5, cx + 5, cy + 3)
            } else g.drawRect(cx - 5, cy - 5, 10, 10)
            Kind.CLOSE -> {
                g.drawLine(cx - 5, cy - 5, cx + 5, cy + 5)
                g.drawLine(cx + 5, cy - 5, cx - 5, cy + 5)
            }
        }
    }

    override fun onMouseEvent(event: InputEvent.MouseInput): Boolean = when (event) {
        is InputEvent.MousePressed -> { requestRepaint(); true }
        is InputEvent.MouseReleased -> {
            requestRepaint()
            if (bounds.contains(event.x, event.y)) controls?.let(action)
            true
        }
        is InputEvent.MouseEntered, is InputEvent.MouseExited, is InputEvent.MouseCancelled -> { requestRepaint(); true }
        else -> false
    }

    companion object {
        const val WIDTH = 46
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
    g.color = theme.separator; g.drawRect(0, 0, bounds.width - 1, bounds.height - 1); g.drawLine(0, 38, bounds.width, 38)
    g.font = theme.labelFont.deriveFont(13f); g.color = theme.onSurface; g.drawString(title, 14, 25)
}

private fun blend(base: Color, overlay: Color, alpha: Float): Color {
    val inverse = 1f - alpha
    return Color((base.red * inverse + overlay.red * alpha).toInt(), (base.green * inverse + overlay.green * alpha).toInt(), (base.blue * inverse + overlay.blue * alpha).toInt())
}

/** Analyzer that finds nothing, for files that aren't prose. */
private object NoMorphemeAnalyzer : MorphemeAnalyzer {
    override fun analyze(text: String, baseOffset: Int): List<Morpheme> = emptyList()
}

private fun analyzerFor(path: Path): MorphemeAnalyzer =
    if (NovelFormat.of(path) != null || path.fileName.toString().endsWith(".md", ignoreCase = true)) Morphology.analyzer else NoMorphemeAnalyzer
