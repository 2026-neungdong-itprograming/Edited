package io.github.nd2026.edited.ui.panes

import io.github.nd2026.edited.event.InputEvent
import io.github.nd2026.edited.project.Project
import io.github.nd2026.edited.ui.Widget
import io.github.nd2026.edited.world.Appearance
import io.github.nd2026.edited.world.CharacterRepository
import io.github.nd2026.edited.world.CharacterSheet
import io.github.nd2026.edited.world.CharacterUsage
import io.github.nd2026.edited.world.WorldRepository
import java.awt.Font
import java.awt.Graphics2D
import java.awt.Rectangle

/** 설정집: character and world cards on top, the selectedCard card's details (and where it appears) below. */
class CharactersPane : Widget() {
    private sealed interface Item {
        val name: String
        data class Section(override val name: String) : Item
        data class Card(override val name: String, val aliases: List<String>, val description: String, val character: CharacterSheet?) : Item
    }

    var project: Project? = null
        set(value) { field = value; refresh() }

    private var items = emptyList<Item>()
    private var selectedCard: Item.Card? = null
    private var appearances = emptyList<Appearance>()

    /** Re-reads `characters/` and `world/`; call after files change. */
    fun refresh() {
        val p = project
        items = if (p == null) emptyList() else buildList {
            val characters = runCatching { CharacterRepository.load(p) }.getOrDefault(emptyList())
            val world = runCatching { WorldRepository.load(p) }.getOrDefault(emptyList())
            add(Item.Section("인물  ${characters.size}"))
            characters.forEach { add(Item.Card(it.name, it.aliases, it.description, it)) }
            add(Item.Section("세계관  ${world.size}"))
            world.forEach { add(Item.Card(it.name, it.aliases, it.description, null)) }
        }
        select(items.filterIsInstance<Item.Card>().firstOrNull { it.name == selectedCard?.name })
    }

    private fun select(card: Item.Card?) {
        selectedCard = card
        val p = project
        appearances = if (p != null && card?.character != null) runCatching { CharacterUsage.appearances(p, card.character) }.getOrDefault(emptyList()) else emptyList()
        requestRepaint()
    }

    private val listHeight get() = (bounds.height * .45).toInt()

    override fun onPaint(g: Graphics2D, localRegion: Rectangle) {
        PaneStyle.paintPanel(this, g, "설정집")
        g.font = theme.labelFont
        val fm = g.fontMetrics
        if (project == null) { g.color = theme.outline; g.drawString("프로젝트를 여세요", 14, 62); return }
        val listBottom = PaneStyle.HEADER_HEIGHT + listHeight
        items.forEachIndexed { i, item ->
            val y = FIRST_BASELINE + i * ROW_HEIGHT
            if (y > listBottom) return@forEachIndexed
            when (item) {
                is Item.Section -> { g.color = theme.outline; g.drawString(item.name, 14, y) }
                is Item.Card -> {
                    if (item == selectedCard) {
                        g.color = PaneStyle.blend(theme.surface, theme.primary, .14f)
                        g.fillRoundRect(8, y - 17, bounds.width - 16, ROW_HEIGHT - 2, 8, 8)
                    }
                    g.color = theme.onSurface
                    g.drawString(PaneStyle.ellipsize("  " + item.name, fm, bounds.width - 28), 14, y)
                }
            }
        }
        g.color = theme.outline
        g.drawLine(0, listBottom + 4, bounds.width, listBottom + 4)

        val card = selectedCard ?: run { g.drawString("카드를 선택하세요", 14, listBottom + 28); return }
        var y = listBottom + 28
        g.font = theme.labelFont.deriveFont(Font.BOLD)
        g.color = theme.onSurface; g.drawString(PaneStyle.ellipsize(card.name, g.fontMetrics, bounds.width - 28), 14, y)
        g.font = theme.labelFont
        if (card.aliases.isNotEmpty()) {
            y += 20; g.color = theme.outline; g.drawString(PaneStyle.ellipsize("별칭: " + card.aliases.joinToString(", "), fm, bounds.width - 28), 14, y)
        }
        if (card.character != null) {
            y += 20; g.color = theme.primary
            val summary = if (appearances.isEmpty()) "원고에 아직 나오지 않음"
            else "${appearances.size}개 회차 · ${appearances.sumOf { it.count }}회 등장 (첫 등장: ${appearances.first().title})"
            g.drawString(PaneStyle.ellipsize(summary, fm, bounds.width - 28), 14, y)
        }
        y += 8
        g.color = theme.onSurface
        for (line in PaneStyle.wrap(card.description, fm, bounds.width - 28)) {
            y += 18
            if (y > bounds.height - 6) break
            g.drawString(line, 14, y)
        }
    }

    override fun onMouseEvent(event: InputEvent.MouseInput): Boolean {
        if (event !is InputEvent.MousePressed) return false
        val localY = event.y - bounds.y
        if (localY <= PaneStyle.HEADER_HEIGHT || localY > PaneStyle.HEADER_HEIGHT + listHeight) return false
        val card = items.getOrNull((localY - (FIRST_BASELINE - 17)) / ROW_HEIGHT) as? Item.Card ?: return false
        select(card)
        return true
    }

    private companion object {
        const val FIRST_BASELINE = 62
        const val ROW_HEIGHT = 24
    }
}
