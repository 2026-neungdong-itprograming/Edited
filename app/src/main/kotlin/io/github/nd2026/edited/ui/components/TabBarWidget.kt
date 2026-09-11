package io.github.nd2026.edited.ui.components

import io.github.nd2026.edited.event.InputEvent
import io.github.nd2026.edited.ui.Semantics
import io.github.nd2026.edited.ui.Widget
import io.github.nd2026.edited.ui.layout.Constraints
import io.github.nd2026.edited.ui.layout.IntSize
import java.awt.BasicStroke
import java.awt.Graphics2D
import java.awt.Rectangle

data class TabItem(val id: String, val title: String, val closable: Boolean = true)

class TabBarWidget(
    tabs: List<TabItem> = emptyList(),
    selectedId: String? = tabs.firstOrNull()?.id,
) : Widget() {
    var tabs: List<TabItem> = tabs
        set(value) {
            field = value
            if (selectedId !in value.map { it.id }) selectedId = value.firstOrNull()?.id
            requestRepaint()
        }

    var selectedId: String? = selectedId
        set(value) {
            if (field == value) return
            require(value == null || tabs.any { it.id == value }) { "selected tab must exist" }
            field = value
            requestRepaint()
        }

    var onSelect: (String) -> Unit = {}
    var onClose: (String) -> Unit = {}

    private val tabHeight = 40
    private val tabWidth = 160

    init {
        focusable = true
        semantics = Semantics(role = Semantics.Role.TAB_LIST, actions = setOf(Semantics.Action.FOCUS))
    }

    override fun onMeasure(constraints: Constraints): IntSize =
        constraints.constrain(IntSize(tabs.size * tabWidth, tabHeight))

    override fun onPaint(g: Graphics2D, localRegion: Rectangle) {
        g.font = theme.labelFont
        tabs.forEachIndexed { index, tab ->
            val x = index * tabWidth
            if (tab.id == selectedId) {
                g.color = theme.primary.withAlpha(24)
                g.fillRect(x, 0, tabWidth, bounds.height)
                g.color = theme.primary
                g.fillRect(x, bounds.height - 3, tabWidth, 3)
            }
            g.color = theme.onSurface
            val title = ellipsize(tab.title, g, if (tab.closable) tabWidth - 52 else tabWidth - 24)
            g.drawString(title, x + 16, (bounds.height + g.fontMetrics.ascent - g.fontMetrics.descent) / 2)
            if (tab.closable) paintClose(g, x + tabWidth - 32, (bounds.height - 20) / 2)
        }
    }

    override fun onMouseEvent(event: InputEvent.MouseInput): Boolean {
        if (event !is InputEvent.MousePressed) return false
        val localX = event.x - bounds.x
        val index = localX / tabWidth
        val tab = tabs.getOrNull(index) ?: return false
        val withinTab = localX - index * tabWidth
        if (tab.closable && withinTab >= tabWidth - 40) {
            onClose(tab.id)
        } else {
            selectedId = tab.id
            onSelect(tab.id)
        }
        return true
    }

    private fun paintClose(g: Graphics2D, x: Int, y: Int) {
        g.stroke = BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.drawLine(x + 5, y + 5, x + 15, y + 15)
        g.drawLine(x + 15, y + 5, x + 5, y + 15)
    }

    private fun ellipsize(text: String, g: Graphics2D, maxWidth: Int): String {
        if (g.fontMetrics.stringWidth(text) <= maxWidth) return text
        var end = text.length
        while (end > 0 && g.fontMetrics.stringWidth(text.take(end) + "…") > maxWidth) end--
        return text.take(end) + "…"
    }
}
