package io.github.nd2026.edited.ui.editor

import io.github.nd2026.edited.core.InspectionModel
import io.github.nd2026.edited.core.Severity
import io.github.nd2026.edited.core.TextArea
import io.github.nd2026.edited.theme.Theme
import java.awt.Color

/** One open manuscript: its text, its inspections, and the tab title. Shared by every split view of it. */
class EditorDocument(
    val id: String,
    var title: String,
    val textArea: TextArea,
    val inspections: InspectionModel = InspectionModel().also { it.attachTo(textArea) },
)

internal fun Theme.colorOf(severity: Severity): Color = when (severity) {
    Severity.ERROR -> error
    Severity.WARNING -> warning
    Severity.INFO -> primary
}
