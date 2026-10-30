package io.github.nd2026.edited

import io.github.nd2026.edited.project.Project
import io.github.nd2026.edited.project.RecentProjects
import io.github.nd2026.edited.ui.Command
import io.github.nd2026.edited.ui.KeyStroke
import io.github.nd2026.edited.ui.NewProjectDialog
import io.github.nd2026.edited.ui.OverlayHostWidget
import io.github.nd2026.edited.ui.OpenProjectDialog
import io.github.nd2026.edited.ui.ProjectWorkspaceWidget
import io.github.nd2026.edited.ui.RootPane
import io.github.nd2026.edited.ui.WindowControls
import io.github.nd2026.edited.ui.WorkspaceSession
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowEvent
import java.nio.file.Files
import java.nio.file.Paths
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.SwingUtilities

fun main(args: Array<String>) {
    // HiDPI 스케일링 활성화
    System.setProperty("sun.java2d.uiScale", "1.0") // 또는 OS 비율 자동 감지: null 처리
    System.setProperty("prism.allowhidpi", "true")

    // 텍스트 렌더링 힌트 설정 (ClearType / 안티앨리어싱)
    System.setProperty("awt.useSystemAAFontSettings", "on") // 또는 "lcd"
    System.setProperty("swing.aatext", "true")

    SwingUtilities.invokeLater {
        TextedWindow(StartupProject.find(args.firstOrNull())).isVisible = true
    }
}

/** The only Swing window. All application UI below it is rendered by the custom widget tree. */
class TextedWindow(initialProject: Project?) : JFrame() {
    private val workspace = ProjectWorkspaceWidget(initialProject?.let(::WorkspaceSession))

    init {
        title = windowTitle(initialProject)
        // No OS title bar: the workspace's top bar draws the caption buttons (IntelliJ-style).
        isUndecorated = true
        size = Dimension(1280, 800)
        minimumSize = Dimension(900, 600)
        defaultCloseOperation = EXIT_ON_CLOSE
        setLocationRelativeTo(null)
        workspace.windowControls = object : WindowControls {
            override val maximized get() = extendedState and MAXIMIZED_BOTH == MAXIMIZED_BOTH
            override fun minimize() { extendedState = extendedState or ICONIFIED }
            override fun toggleMaximize() {
                if (maximized) { extendedState = NORMAL; return }
                // Undecorated frames would cover the taskbar unless the bounds are limited to the work area.
                val screen = graphicsConfiguration.bounds
                val insets = Toolkit.getDefaultToolkit().getScreenInsets(graphicsConfiguration)
                maximizedBounds = Rectangle(screen.x + insets.left, screen.y + insets.top, screen.width - insets.left - insets.right, screen.height - insets.top - insets.bottom)
                extendedState = MAXIMIZED_BOTH
            }
            override fun close() = dispatchEvent(WindowEvent(this@TextedWindow, WindowEvent.WINDOW_CLOSING))
            override fun moveBy(dx: Int, dy: Int) = setLocation(x + dx, y + dy)
        }
        addWindowStateListener { workspace.requestRepaint() }

        val host = OverlayHostWidget(workspace)
        var dialogOpen = false
        val newProject = {
            if (!dialogOpen) {
                dialogOpen = true
                NewProjectDialog { created ->
                    RecentProjects.record(created.project)
                    workspace.openProject(created.project, created.warnings)
                    title = windowTitle(created.project)
                }.also { it.onClosed = { dialogOpen = false } }.show(host)
            }
        }
        val openProject = {
            if (!dialogOpen) {
                dialogOpen = true
                OpenProjectDialog { project ->
                    workspace.openProject(project)
                    title = windowTitle(project)
                }.also { it.onClosed = { dialogOpen = false } }.show(host)
            }
        }
        workspace.onNewProject = newProject
        workspace.onOpenProject = openProject
        initialProject?.let(RecentProjects::record)

        contentPane.add(RootPane().apply {
            content = host
            installResizeBorder(this)
            keymap.bind(shortcut(KeyEvent.VK_N), Command { newProject(); true })
            keymap.bind(shortcut(KeyEvent.VK_O), Command { openProject(); true })
            keymap.bind(shortcut(KeyEvent.VK_S), Command { workspace.saveActiveDocument(); true })
            keymap.bind(shortcut(KeyEvent.VK_F, shift = true), Command { workspace.showSearch(); true })
            keymap.bind(shortcut(KeyEvent.VK_E, shift = true), Command { workspace.exportManuscript(); true })
            keymap.bind(shortcut(KeyEvent.VK_C, shift = true), Command { workspace.copyActiveChapter(); true })
            keymap.bind(shortcut(KeyEvent.VK_R, shift = true), Command { workspace.polishSelection(); true })
            keymap.bind(shortcut(KeyEvent.VK_F4), Command { workspace.closeActiveTab(); true })
            keymap.bind(shortcut(KeyEvent.VK_K), Command { workspace.commitNow(); true })
            keymap.bind(KeyStroke(KeyEvent.VK_F2, 0), Command { workspace.gotoProblem(1); true })
            keymap.bind(KeyStroke(KeyEvent.VK_F2, InputEvent.SHIFT_DOWN_MASK), Command { workspace.gotoProblem(-1); true })
            keymap.bind(KeyStroke(KeyEvent.VK_RIGHT, InputEvent.ALT_DOWN_MASK), Command { workspace.selectTab(1); true })
            keymap.bind(KeyStroke(KeyEvent.VK_LEFT, InputEvent.ALT_DOWN_MASK), Command { workspace.selectTab(-1); true })
            for (i in 0..4) keymap.bind(KeyStroke(KeyEvent.VK_1 + i, InputEvent.ALT_DOWN_MASK), Command { workspace.showLeftPane(i); true })
            // Menu mnemonics, matching the 파일(F) … 도움말(H) titles.
            listOf(KeyEvent.VK_F, KeyEvent.VK_E, KeyEvent.VK_V, KeyEvent.VK_N, KeyEvent.VK_T, KeyEvent.VK_G, KeyEvent.VK_H).forEachIndexed { i, key ->
                keymap.bind(KeyStroke(key, InputEvent.ALT_DOWN_MASK), Command { workspace.openMenu(i); true })
            }
        })
    }

    /** Lets the undecorated frame be resized by dragging within [RESIZE_MARGIN] pixels of its edges. */
    private fun installResizeBorder(target: JComponent) {
        val handler = object : MouseAdapter() {
            private var edges = 0
            private var start = Point()
            private var startBounds = Rectangle()

            private fun edgesAt(x: Int, y: Int): Int {
                if (extendedState and MAXIMIZED_BOTH != 0) return 0
                var result = 0
                if (x < RESIZE_MARGIN) result = result or WEST
                if (x >= target.width - RESIZE_MARGIN) result = result or EAST
                if (y < RESIZE_MARGIN) result = result or NORTH
                if (y >= target.height - RESIZE_MARGIN) result = result or SOUTH
                return result
            }

            override fun mouseMoved(e: MouseEvent) {
                val cursor = when (edgesAt(e.x, e.y)) {
                    WEST -> Cursor.W_RESIZE_CURSOR
                    EAST -> Cursor.E_RESIZE_CURSOR
                    NORTH -> Cursor.N_RESIZE_CURSOR
                    SOUTH -> Cursor.S_RESIZE_CURSOR
                    NORTH or WEST -> Cursor.NW_RESIZE_CURSOR
                    NORTH or EAST -> Cursor.NE_RESIZE_CURSOR
                    SOUTH or WEST -> Cursor.SW_RESIZE_CURSOR
                    SOUTH or EAST -> Cursor.SE_RESIZE_CURSOR
                    else -> Cursor.DEFAULT_CURSOR
                }
                if (target.cursor.type != cursor) target.cursor = Cursor.getPredefinedCursor(cursor)
            }

            override fun mousePressed(e: MouseEvent) {
                edges = edgesAt(e.x, e.y)
                start = e.locationOnScreen
                startBounds = bounds
            }

            override fun mouseDragged(e: MouseEvent) {
                if (edges == 0) return
                val dx = e.locationOnScreen.x - start.x
                val dy = e.locationOnScreen.y - start.y
                val next = Rectangle(startBounds)
                if (edges and EAST != 0) next.width = maxOf(minimumSize.width, startBounds.width + dx)
                if (edges and SOUTH != 0) next.height = maxOf(minimumSize.height, startBounds.height + dy)
                if (edges and WEST != 0) {
                    next.width = maxOf(minimumSize.width, startBounds.width - dx)
                    next.x = startBounds.x + startBounds.width - next.width
                }
                if (edges and NORTH != 0) {
                    next.height = maxOf(minimumSize.height, startBounds.height - dy)
                    next.y = startBounds.y + startBounds.height - next.height
                }
                bounds = next
            }

            override fun mouseReleased(e: MouseEvent) {
                edges = 0
            }
        }
        target.addMouseListener(handler)
        target.addMouseMotionListener(handler)
    }

    private fun shortcut(keyCode: Int, shift: Boolean = false) =
        KeyStroke(keyCode, Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx or if (shift) InputEvent.SHIFT_DOWN_MASK else 0)
    private fun windowTitle(project: Project?) = project?.let { "Texted — ${it.title}" } ?: "Texted"

    private companion object {
        const val RESIZE_MARGIN = 5
        const val WEST = 1
        const val EAST = 2
        const val NORTH = 4
        const val SOUTH = 8
    }
}

/** Resolves an actual on-disk project without creating files behind the user's back. */
private object StartupProject {
    fun find(argument: String?): Project? {
        val current = Paths.get("").toAbsolutePath()
        val candidates = listOfNotNull(
            argument?.takeIf(String::isNotBlank)?.let(Paths::get),
            System.getProperty("texted.project")?.takeIf(String::isNotBlank)?.let(Paths::get),
            System.getenv("TEXTED_PROJECT")?.takeIf(String::isNotBlank)?.let(Paths::get),
            current,
            RecentProjects.list().firstOrNull(),
        )
        return candidates.firstOrNull { Files.isDirectory(it) && Project.isProject(it) }?.let(Project::open)
    }
}
