package io.github.nd2026.edited.ui.components

import io.github.nd2026.edited.event.InputEvent
import io.github.nd2026.edited.ui.Semantics
import io.github.nd2026.edited.ui.Widget
import io.github.nd2026.edited.ui.layout.Constraints
import io.github.nd2026.edited.ui.layout.IntSize
import java.awt.BasicStroke
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints

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

    private val feedback = HashMap<String, PressFeedback>()
    private val closeFeedback = HashMap<String, PressFeedback>()
    private var hoverId: String? = null
    private var hoverClose = false
    private var pressedId: String? = null
    private var pressedClose = false

    private fun feedbackOf(map: HashMap<String, PressFeedback>, id: String) = map.getOrPut(id) { PressFeedback { requestRepaint() } }

    override fun onPaint(g: Graphics2D, localRegion: Rectangle) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.font = theme.labelFont
        val ids = tabs.map { it.id }.toSet()
        feedback.keys.retainAll(ids)
        closeFeedback.keys.retainAll(ids)
        tabs.forEachIndexed { index, tab ->
            val x = index * tabWidth
            val tabFeedback = feedbackOf(feedback, tab.id)
            tabFeedback.update(tab.id == hoverId && !hoverClose, tab.id == pressedId && !pressedClose)
            if (tab.id == selectedId) {
                g.color = theme.primary.withAlpha(24)
                g.fillRect(x, 0, tabWidth, bounds.height)
                g.color = theme.primary
                g.fillRect(x, bounds.height - 3, tabWidth, 3)
            }
            tabFeedback.subtleFill(theme)?.let { g.color = it; g.fillRect(x, 0, tabWidth, bounds.height - 3) }
            g.color = tabFeedback.content(theme.onSurface)
            val title = ellipsize(tab.title, g, if (tab.closable) tabWidth - 52 else tabWidth - 24)
            g.drawString(title, x + 16, (bounds.height + g.fontMetrics.ascent - g.fontMetrics.descent) / 2)
            if (tab.closable) {
                val close = feedbackOf(closeFeedback, tab.id)
                close.update(tab.id == hoverId && hoverClose, tab.id == pressedId && pressedClose)
                val cx = x + tabWidth - 32
                val cy = (bounds.height - 20) / 2
                close.subtleFill(theme)?.let { g.color = it; g.fillRoundRect(cx - 2, cy - 2, 24, 24, PressFeedback.CORNER_ARC, PressFeedback.CORNER_ARC) }
                g.color = close.content(theme.onSurface)
                paintClose(g, cx, cy)
            }
        }
    }

    /** The tab under [x] (root coordinates) and whether the point is on its close button. */
    private fun hitAt(x: Int): Pair<TabItem, Boolean>? {
        val localX = x - bounds.x
        if (localX < 0) return null
        val index = localX / tabWidth
        val tab = tabs.getOrNull(index) ?: return null
        return tab to (tab.closable && localX - index * tabWidth >= tabWidth - 40)
    }

    private fun setHover(x: Int?) {
        val hit = x?.let(::hitAt)
        val id = hit?.first?.id
        val onClose = hit?.second == true
        if (id == hoverId && onClose == hoverClose) return
        hoverId = id
        hoverClose = onClose
        requestRepaint()
    }

    override fun onMouseEvent(event: InputEvent.MouseInput): Boolean = when (event) {
        is InputEvent.MousePressed -> {
            val (tab, onClose) = hitAt(event.x) ?: return false
            pressedId = tab.id
            pressedClose = onClose
            // Tabs activate on press (like Windows); closing waits for the release, so it can be cancelled.
            if (!onClose) {
                selectedId = tab.id
                onSelect(tab.id)
            }
            requestRepaint()
            true
        }
        is InputEvent.MouseReleased -> {
            val hit = hitAt(event.x)?.takeIf { bounds.contains(event.x, event.y) }
            val closeId = pressedId?.takeIf { pressedClose && hit != null && hit.first.id == it && hit.second }
            pressedId = null
            pressedClose = false
            setHover(event.x.takeIf { bounds.contains(event.x, event.y) })
            requestRepaint()
            closeId?.let(onClose)
            true
        }
        is InputEvent.MouseMoved, is InputEvent.MouseEntered -> { setHover(event.x); false }
        is InputEvent.MouseExited -> { setHover(null); false }
        is InputEvent.MouseCancelled -> { pressedId = null; pressedClose = false; setHover(null); true }
        else -> false
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
