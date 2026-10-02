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
import io.github.nd2026.edited.ui.WorkspaceSession
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.event.KeyEvent
import java.nio.file.Files
import java.nio.file.Paths
import javax.swing.JFrame
import javax.swing.SwingUtilities

fun main(args: Array<String>) = SwingUtilities.invokeLater {
    TextedWindow(StartupProject.find(args.firstOrNull())).isVisible = true
}

/** The only Swing window. All application UI below it is rendered by the custom widget tree. */
class TextedWindow(initialProject: Project?) : JFrame() {
    private val workspace = ProjectWorkspaceWidget(initialProject?.let(::WorkspaceSession))

    init {
        title = windowTitle(initialProject)
        size = Dimension(1280, 800)
        minimumSize = Dimension(900, 600)
        defaultCloseOperation = EXIT_ON_CLOSE
        setLocationRelativeTo(null)

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
            keymap.bind(shortcut(KeyEvent.VK_N), Command { newProject(); true })
            keymap.bind(shortcut(KeyEvent.VK_O), Command { openProject(); true })
            keymap.bind(shortcut(KeyEvent.VK_S), Command { workspace.saveActiveDocument(); true })
        })
    }

    private fun shortcut(keyCode: Int) = KeyStroke(keyCode, Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx)
    private fun windowTitle(project: Project?) = project?.let { "Texted — ${it.title}" } ?: "Texted"
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
