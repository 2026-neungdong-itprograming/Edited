package io.github.nd2026.edited.ui.components

import io.github.nd2026.edited.ui.Semantics
import io.github.nd2026.edited.ui.Widget
import io.github.nd2026.edited.ui.layout.Constraints
import io.github.nd2026.edited.ui.layout.IntSize
import java.awt.Font
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.font.FontRenderContext
import kotlin.math.ceil

class TextWidget(
    text: String,
    var style: Style = Style.BODY,
) : Widget() {
    enum class Style { BODY, LABEL, MONOSPACE }

    var text: String = text
        set(value) {
            if (field == value) return
            field = value
            semantics = semantics.copy(name = value)
            requestRepaint()
        }

    init {
        semantics = Semantics(role = Semantics.Role.TEXT, name = text)
    }

    private fun font(): Font = when (style) {
        Style.BODY -> theme.bodyFont
        Style.LABEL -> theme.labelFont
        Style.MONOSPACE -> theme.monospaceFont
    }

    override fun onMeasure(constraints: Constraints): IntSize {
        val font = font()
        val context = FontRenderContext(null, true, true)
        val bounds = font.getStringBounds(text, context)
        val metrics = font.getLineMetrics(text.ifEmpty { " " }, context)
        return constraints.constrain(IntSize(ceil(bounds.width).toInt(), ceil(metrics.height).toInt()))
    }

    override fun onPaint(g: Graphics2D, localRegion: Rectangle) {
        g.font = font()
        g.color = if (enabled) theme.onSurface else theme.onSurface.withAlpha(97)
        g.drawString(text, 0, g.fontMetrics.ascent)
    }
}
