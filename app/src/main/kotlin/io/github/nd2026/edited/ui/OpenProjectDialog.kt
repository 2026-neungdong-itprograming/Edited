package io.github.nd2026.edited.ui

import io.github.nd2026.edited.project.Project
import io.github.nd2026.edited.project.RecentProjects
import io.github.nd2026.edited.ui.components.ButtonWidget
import io.github.nd2026.edited.ui.components.DialogWidget
import io.github.nd2026.edited.ui.components.TextFieldWidget
import java.awt.Graphics2D
import java.awt.Rectangle
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

/** Opens a project folder by path and exposes the persistent recent-project list. */
class OpenProjectDialog(private val onOpened: (Project) -> Unit) {
    private val pathField = TextFieldWidget(accessibleLabel = "프로젝트 폴더", placeholder = "texted.project.json이 있는 폴더")
    private val recent = RecentProjects.list().take(MAX_RECENT)
    private val body = OpenProjectBody(pathField, recent, ::open)
    private val cancelButton = ButtonWidget("취소")
    private val openButton = ButtonWidget("열기", ButtonWidget.Style.FILLED)
    private val dialog = DialogWidget("프로젝트 열기", body, OpenProjectBody.heightFor(recent.size), listOf(cancelButton, openButton), 560)
    var onClosed: () -> Unit = {}

    init {
        cancelButton.onClick = ::close
        openButton.onClick = { open(pathField.text) }
        pathField.onSubmit = { open(pathField.text) }
        pathField.onChange = { body.error = null; pathField.invalid = false; body.requestRepaint() }
        dialog.onCancel = ::close
    }

    fun show(host: OverlayHostWidget) = dialog.show(host)

    private fun open(rawPath: String) {
        val path = try {
            Path.of(rawPath.trim()).toAbsolutePath().normalize()
        } catch (_: InvalidPathException) {
            showError("올바른 폴더 경로를 입력하세요.")
            return
        }
        when {
            rawPath.isBlank() -> showError("프로젝트 폴더를 입력하세요.")
            !Files.isDirectory(path) -> showError("폴더를 찾을 수 없습니다: $path")
            !Project.isProject(path) -> showError("Texted 프로젝트가 아닙니다: texted.project.json이 없습니다.")
            else -> runCatching { Project.open(path) }
                .onSuccess { project -> RecentProjects.record(project); close(); onOpened(project) }
                .onFailure { showError("프로젝트를 열 수 없습니다: ${it.message}") }
        }
    }

    private fun showError(message: String) {
        pathField.invalid = true
        body.error = message
        body.requestRepaint()
    }

    private fun close() {
        dialog.close()
        onClosed()
    }

    private class OpenProjectBody(
        private val pathField: TextFieldWidget,
        recentPaths: List<Path>,
        private val onOpen: (String) -> Unit,
    ) : Container() {
        private val recentButtons = recentPaths.map { path ->
            ButtonWidget("${path.fileName}  —  $path").apply { onClick = { onOpen(path.toString()) } }
        }
        var error: String? = null

        init {
            addChild(pathField)
            recentButtons.forEach(::addChild)
        }

        override fun layout() {
            pathField.setBounds(bounds.x, bounds.y + 22, bounds.width, TextFieldWidget.HEIGHT)
            recentButtons.forEachIndexed { index, button ->
                button.setBounds(bounds.x, bounds.y + 92 + index * RECENT_ROW, bounds.width, ButtonWidget.HEIGHT)
            }
        }

        override fun onPaint(g: Graphics2D, localRegion: Rectangle) {
            g.font = theme.labelFont
            g.color = theme.onSurface
            g.drawString("프로젝트 폴더", 4, g.fontMetrics.ascent)
            g.drawString("최근 프로젝트", 4, 80)
            if (recentButtons.isEmpty()) {
                g.color = theme.outline
                g.drawString("최근에 연 프로젝트가 없습니다.", 4, 108)
            }
            error?.let {
                g.color = theme.error
                g.drawString(it, 4, bounds.height - 4)
            }
        }

        companion object {
            private const val RECENT_ROW = 44
            fun heightFor(count: Int) = 112 + count * RECENT_ROW + 24
        }
    }

    companion object { private const val MAX_RECENT = 5 }
}
