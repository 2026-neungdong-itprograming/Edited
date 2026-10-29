package io.github.nd2026.edited.ui.panes

import io.github.nd2026.edited.core.Inspection
import io.github.nd2026.edited.core.Severity
import io.github.nd2026.edited.event.InputEvent
import io.github.nd2026.edited.project.Project
import io.github.nd2026.edited.ui.Widget
import java.awt.Graphics2D
import java.awt.Rectangle
import java.nio.file.Path

/** Bottom pane listing project problems and every chapter's inspections; a click jumps to the line. */
class ProblemsPane : Widget() {
    private data class Row(val path: Path?, val line: Int, val severity: Severity, val text: String)

    var project: Project? = null
        set(value) { field = value; rebuild() }

    /** Inspections per chapter file, as produced by `Linter.run`. */
    var inspections: Map<Path, List<Inspection>> = emptyMap()
        set(value) { field = value; rebuild() }

    var onNavigate: (Path, Int) -> Unit = { _, _ -> }

    private var rows = emptyList<Row>()
    private var firstRow = 0

    private fun rebuild() {
        val projectRows = buildList {
            project?.let {
                if (it.chapters().isEmpty()) add(Row(null, 0, Severity.WARNING, "원고 파일이 없습니다."))
                if (it.manifest.author.isBlank()) add(Row(null, 0, Severity.INFO, "작가명이 지정되지 않았습니다."))
            }
        }
        val fileRows = inspections.entries.flatMap { (path, list) ->
            list.map { Row(path, it.line, it.severity, "${path.fileName}:${it.line + 1}  ${it.message}") }
        }.sortedWith(compareBy({ -it.severity.ordinal }, { it.path.toString() }, { it.line }))
        rows = projectRows + fileRows
        firstRow = firstRow.coerceIn(0, (rows.size - 1).coerceAtLeast(0))
        requestRepaint()
    }

    override fun onPaint(g: Graphics2D, localRegion: Rectangle) {
        val errors = rows.count { it.severity == Severity.ERROR }
        val warnings = rows.count { it.severity == Severity.WARNING }
        PaneStyle.paintPanel(this, g, "Problems  ${rows.size}   (오류 $errors · 경고 $warnings)")
        g.font = theme.labelFont
        if (rows.isEmpty()) {
            g.color = theme.outline; g.drawString("발견된 문제가 없습니다.", 18, 68); return
        }
        val fm = g.fontMetrics
        rows.drop(firstRow).forEachIndexed { i, row ->
            val y = FIRST_BASELINE + i * ROW_HEIGHT
            if (y > bounds.height) return
            g.color = when (row.severity) {
                Severity.ERROR -> theme.error
                Severity.WARNING -> theme.warning
                Severity.INFO -> theme.primary
            }
            g.drawString(if (row.severity == Severity.INFO) "●" else "▲", 18, y)
            g.color = theme.onSurface
            g.drawString(PaneStyle.ellipsize(row.text, fm, bounds.width - 60), 42, y)
        }
    }

    override fun onMouseEvent(event: InputEvent.MouseInput): Boolean = when (event) {
        is InputEvent.MousePressed -> {
            val index = firstRow + (event.y - bounds.y - (FIRST_BASELINE - 18)) / ROW_HEIGHT
            val row = rows.getOrNull(index)?.takeIf { event.y - bounds.y > PaneStyle.HEADER_HEIGHT }
            row?.path?.let { onNavigate(it, row.line) }
            row?.path != null
        }
        is InputEvent.Scroll -> {
            firstRow = (firstRow + PaneStyle.scrollRows(event.unitsToScroll)).coerceIn(0, (rows.size - 1).coerceAtLeast(0))
            requestRepaint(); true
        }
        else -> false
    }

    private companion object {
        const val FIRST_BASELINE = 64
        const val ROW_HEIGHT = 24
    }
}
