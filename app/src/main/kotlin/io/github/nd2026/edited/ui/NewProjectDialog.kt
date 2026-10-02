package io.github.nd2026.edited.ui

import io.github.nd2026.edited.project.CreatedProject
import io.github.nd2026.edited.project.NewProjectRequest
import io.github.nd2026.edited.project.ProjectService
import io.github.nd2026.edited.ui.components.ButtonWidget
import io.github.nd2026.edited.ui.components.DialogWidget
import io.github.nd2026.edited.ui.components.TextFieldWidget
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.nio.file.InvalidPathException
import java.nio.file.Paths
import javax.swing.SwingUtilities

/**
 * "새 프로젝트" dialog: collects title, author and parent folder, validates through
 * [ProjectService.validate], and creates the project on a background thread (folder creation
 * plus the initial Git commit can take a moment) before reporting back on the EDT.
 */
class NewProjectDialog(private val onCreated: (CreatedProject) -> Unit) {

    private val titleField = TextFieldWidget(accessibleLabel = "작품 제목", placeholder = "예: 달빛 아래의 계약")
    private val authorField = TextFieldWidget(accessibleLabel = "작가명", placeholder = "선택 사항")
    private val locationField = TextFieldWidget(ProjectService.defaultLocation(), accessibleLabel = "저장 위치")
    private val form = FormBody(titleField, authorField, locationField)
    private val cancelButton = ButtonWidget("취소")
    private val createButton = ButtonWidget("만들기", ButtonWidget.Style.FILLED)
    private val dialog = DialogWidget("새 프로젝트", form, FormBody.HEIGHT, listOf(cancelButton, createButton))
    private var creating = false

    /** Invoked once the dialog has been removed, whether created or cancelled. */
    var onClosed: () -> Unit = {}

    init {
        cancelButton.onClick = ::cancel
        createButton.onClick = ::submit
        dialog.onCancel = ::cancel
        listOf(titleField, authorField, locationField).forEach { field ->
            field.onSubmit = ::submit
            field.onChange = {
                // Editing clears a stale error and refreshes the "생성될 폴더" preview.
                form.error = null
                titleField.invalid = false
                locationField.invalid = false
                form.requestRepaint()
            }
        }
    }

    fun show(host: OverlayHostWidget) = dialog.show(host)

    private fun cancel() {
        if (!creating) close()
    }

    private fun close() {
        dialog.close()
        onClosed()
    }

    private fun request() = NewProjectRequest(
        title = titleField.text,
        location = locationField.text,
        author = authorField.text,
    )

    private fun submit() {
        if (creating) return
        val request = request()
        val problem = ProjectService.validate(request)
        if (problem != null) {
            titleField.invalid = request.title.isBlank()
            locationField.invalid = request.location.isBlank()
            form.error = problem
            form.requestRepaint()
            return
        }

        creating = true
        createButton.enabled = false
        createButton.label = "만드는 중…"
        Thread({
            val result = runCatching { ProjectService.create(request) }
            SwingUtilities.invokeLater {
                creating = false
                createButton.enabled = true
                createButton.label = "만들기"
                result.onSuccess {
                    close()
                    onCreated(it)
                }.onFailure {
                    form.error = "프로젝트를 만들지 못했습니다: ${it.message ?: it}"
                    form.requestRepaint()
                }
            }
        }, "texted-project-create").apply { isDaemon = true }.start()
    }

    /** Labeled field rows, a live preview of the folder that will be created, and the error line. */
    private class FormBody(
        private val title: TextFieldWidget,
        private val author: TextFieldWidget,
        private val location: TextFieldWidget,
    ) : Container() {
        var error: String? = null

        private val rows = listOf("작품 제목" to title, "작가명" to author, "저장 위치" to location)

        init {
            rows.forEach { addChild(it.second) }
        }

        override fun layout() {
            rows.forEachIndexed { i, (_, field) ->
                field.setBounds(bounds.x, bounds.y + i * ROW + LABEL_HEIGHT, bounds.width, TextFieldWidget.HEIGHT)
            }
        }

        override fun onPaint(g: Graphics2D, localRegion: Rectangle) {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g.font = theme.labelFont
            val fm = g.fontMetrics
            g.color = theme.onSurface
            rows.forEachIndexed { i, (label, _) ->
                g.drawString(label, 4, i * ROW + fm.ascent + 2)
            }

            val previewY = rows.size * ROW + 4
            previewPath()?.let { path ->
                g.color = theme.outline
                var text = "생성될 폴더: $path"
                while (text.length > 12 && fm.stringWidth(text) > bounds.width - 8) text = "…" + text.drop(2)
                g.drawString(text, 4, previewY + fm.ascent)
            }
            error?.let {
                g.color = theme.error
                g.drawString(it, 4, previewY + fm.height + 8 + fm.ascent)
            }
        }

        private fun previewPath(): String? {
            val folder = ProjectService.folderNameFor(title.text)
            if (folder.isEmpty() || location.text.isBlank()) return null
            return try {
                Paths.get(location.text.trim()).resolve(folder).toString()
            } catch (_: InvalidPathException) {
                null
            }
        }

        companion object {
            private const val LABEL_HEIGHT = 18
            private const val ROW = LABEL_HEIGHT + TextFieldWidget.HEIGHT + 14
            const val HEIGHT = 3 * ROW + 48
        }
    }
}
