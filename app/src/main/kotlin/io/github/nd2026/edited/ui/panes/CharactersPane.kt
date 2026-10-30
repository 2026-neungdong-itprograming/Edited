package io.github.nd2026.edited.ui.panes

import io.github.nd2026.edited.event.InputEvent
import io.github.nd2026.edited.project.Project
import io.github.nd2026.edited.ui.Widget
import io.github.nd2026.edited.world.Appearance
import io.github.nd2026.edited.world.CharacterRepository
import io.github.nd2026.edited.world.CharacterSheet
import io.github.nd2026.edited.world.CharacterSuggestion
import io.github.nd2026.edited.world.CharacterSuggestions
import io.github.nd2026.edited.world.CharacterUsage
import io.github.nd2026.edited.world.WorldRepository
import java.awt.Font
import java.awt.Graphics2D
import java.awt.Rectangle

/**
 * 설정집: character and world cards on top, the selected card's details (and where it appears) below. The last
 * section of the list, "추천 인물", holds names the manuscript keeps using that have no card yet (3-6). The slow
 * parts - appearances and suggestions read the whole manuscript - run in the background.
 */
class CharactersPane : Widget() {
    private sealed interface Item {
        val name: String
        data class Section(override val name: String) : Item
        data class Card(override val name: String, val aliases: List<String>, val description: String, val character: CharacterSheet?) : Item
        data class Candidate(val suggestion: CharacterSuggestion) : Item { override val name: String get() = suggestion.name }
    }

    var project: Project? = null
        set(value) { field = value; refresh() }

    private var cards = emptyList<Item>()
    private var items = emptyList<Item>()
    private var selectedItem: Item? = null
    private var appearances = emptyList<Appearance>()
    private var listFirst = 0

    /** Names the manuscript uses at least 3 times that have no card, most used first; empty until computed. */
    var suggestions: List<CharacterSuggestion> = emptyList()
        private set

    private val appearanceWork = BackgroundWork()
    private val suggestionWork = BackgroundWork()

    /** Waits for the running background analyses (tests only; the app has an event loop to wait on). */
    internal fun awaitIdle(timeoutMillis: Long = 30_000) = appearanceWork.awaitIdle(timeoutMillis) && suggestionWork.awaitIdle(timeoutMillis)

    /** Re-reads `characters/` and `world/`; call after files change. */
    fun refresh() {
        val p = project
        cards = if (p == null) emptyList() else buildList {
            val characters = runCatching { CharacterRepository.load(p) }.getOrDefault(emptyList())
            val world = runCatching { WorldRepository.load(p) }.getOrDefault(emptyList())
            add(Item.Section("인물  ${characters.size}"))
            characters.forEach { add(Item.Card(it.name, it.aliases, it.description, it)) }
            add(Item.Section("세계관  ${world.size}"))
            world.forEach { add(Item.Card(it.name, it.aliases, it.description, null)) }
        }
        rebuildItems()
        if (p == null) {
            suggestions = emptyList()
            suggestionWork.cancel()
            rebuildItems()
        } else {
            suggestionWork.run({ CharacterSuggestions.suggest(p) }) { suggestions = it; rebuildItems(); requestRepaint() }
        }
        // Keep the same card (or suggestion) selected across a refresh.
        val previous = selectedItem
        select(previous?.let { p -> items.firstOrNull { it !is Item.Section && it.name == p.name && it::class == p::class } })
    }

    private fun rebuildItems() {
        items = if (project == null) emptyList() else cards + Item.Section("추천 인물  ${suggestions.size}") + suggestions.map { Item.Candidate(it) }
        listFirst = listFirst.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    }

    private fun select(item: Item?) {
        selectedItem = item
        val p = project
        val character = (item as? Item.Card)?.character
        appearances = emptyList()
        if (p != null && character != null) {
            appearanceWork.run({ CharacterUsage.appearances(p, character) }) { appearances = it; requestRepaint() }
        } else {
            appearanceWork.cancel()
        }
        requestRepaint()
    }

    private val listHeight get() = (bounds.height * .45).toInt()

    override fun onPaint(g: Graphics2D, localRegion: Rectangle) {
        PaneStyle.paintPanel(this, g, "설정집")
        g.font = theme.labelFont
        val fm = g.fontMetrics
        if (project == null) { g.color = theme.outline; g.drawString("프로젝트를 여세요", 14, 62); return }
        val listBottom = PaneStyle.HEADER_HEIGHT + listHeight
        for (i in listFirst until items.size) {
            val item = items[i]
            val y = FIRST_BASELINE + (i - listFirst) * ROW_HEIGHT
            if (y > listBottom) break
            when (item) {
                is Item.Section -> { g.color = theme.outline; g.drawString(item.name, 14, y) }
                is Item.Card, is Item.Candidate -> {
                    if (item == selectedItem) {
                        g.color = PaneStyle.blend(theme.surface, theme.primary, .14f)
                        g.fillRoundRect(8, y - 17, bounds.width - 16, ROW_HEIGHT - 2, 8, 8)
                    }
                    var nameWidth = bounds.width - 28
                    if (item is Item.Candidate) {
                        val usage = "${item.suggestion.count}회"
                        g.color = theme.outline
                        g.drawString(usage, bounds.width - 14 - fm.stringWidth(usage), y)
                        nameWidth -= fm.stringWidth(usage) + 8
                    }
                    g.color = theme.onSurface
                    g.drawString(PaneStyle.ellipsize("  " + item.name, fm, nameWidth), 14, y)
                }
            }
        }
        g.color = theme.outline
        g.drawLine(0, listBottom + 4, bounds.width, listBottom + 4)

        when (val item = selectedItem) {
            null -> g.drawString("카드를 선택하세요", 14, listBottom + 28)
            is Item.Card -> paintCard(g, item, listBottom)
            is Item.Candidate -> paintCandidate(g, item.suggestion, listBottom)
            is Item.Section -> {}
        }
    }

    private fun paintCard(g: Graphics2D, card: Item.Card, listBottom: Int) {
        val fm = g.fontMetrics
        var y = listBottom + 28
        g.font = theme.labelFont.deriveFont(Font.BOLD)
        g.color = theme.onSurface; g.drawString(PaneStyle.ellipsize(card.name, g.fontMetrics, bounds.width - 28), 14, y)
        g.font = theme.labelFont
        if (card.aliases.isNotEmpty()) {
            y += 20; g.color = theme.outline; g.drawString(PaneStyle.ellipsize("별칭: " + card.aliases.joinToString(", "), fm, bounds.width - 28), 14, y)
        }
        if (card.character != null) {
            y += 20; g.color = theme.primary
            val summary = when {
                appearanceWork.busy -> "원고에서 찾는 중…"
                appearances.isEmpty() -> "원고에 아직 나오지 않음"
                else -> "${appearances.size}개 회차 · ${appearances.sumOf { it.count }}회 등장 (첫 등장: ${appearances.first().title})"
            }
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

    private fun paintCandidate(g: Graphics2D, candidate: CharacterSuggestion, listBottom: Int) {
        val fm = g.fontMetrics
        var y = listBottom + 28
        g.font = theme.labelFont.deriveFont(Font.BOLD)
        g.color = theme.onSurface; g.drawString(PaneStyle.ellipsize(candidate.name, g.fontMetrics, bounds.width - 28), 14, y)
        g.font = theme.labelFont
        y += 20; g.color = theme.primary
        g.drawString(PaneStyle.ellipsize("원고에 ${candidate.count}회 · ${candidate.chapters}개 회차에 나옵니다", fm, bounds.width - 28), 14, y)
        y += 8
        g.color = theme.outline
        val hint = "설정집에 아직 없는 이름입니다. 원고 패널에서 우클릭 › 새로 만들기 › 캐릭터로 카드를 만들 수 있습니다."
        for (line in PaneStyle.wrap(hint, fm, bounds.width - 28)) {
            y += 18
            if (y > bounds.height - 6) break
            g.drawString(line, 14, y)
        }
    }

    private fun rowAt(localY: Int): Item? = items.getOrNull(listFirst + (localY - (FIRST_BASELINE - 17)) / ROW_HEIGHT)

    override fun onMouseEvent(event: InputEvent.MouseInput): Boolean {
        val localY = event.y - bounds.y
        val inList = localY > PaneStyle.HEADER_HEIGHT && localY <= PaneStyle.HEADER_HEIGHT + listHeight
        return when (event) {
            is InputEvent.MousePressed -> {
                val item = if (inList) rowAt(localY) else null
                if (item == null || item is Item.Section) return false
                select(item)
                true
            }
            is InputEvent.Scroll -> {
                if (!inList) return false
                listFirst = (listFirst + PaneStyle.scrollRows(event.unitsToScroll)).coerceIn(0, (items.size - 1).coerceAtLeast(0))
                requestRepaint()
                true
            }
            else -> false
        }
    }

    private companion object {
        const val FIRST_BASELINE = 62
        const val ROW_HEIGHT = 24
    }
}
