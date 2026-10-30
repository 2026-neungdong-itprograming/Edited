package io.github.nd2026.edited.ui.panes

import io.github.nd2026.edited.event.InputEvent
import io.github.nd2026.edited.project.Project
import io.github.nd2026.edited.search.LemmaSearch
import io.github.nd2026.edited.search.ProjectReplace
import io.github.nd2026.edited.search.ProjectSearch
import io.github.nd2026.edited.search.ReplacePreview
import io.github.nd2026.edited.search.SearchHit
import io.github.nd2026.edited.ui.Container
import io.github.nd2026.edited.ui.components.ButtonWidget
import io.github.nd2026.edited.ui.components.TextFieldWidget
import java.awt.Graphics2D
import java.awt.Rectangle

/**
 * Manuscript-wide find (and replace-all). With an empty replacement field the list shows matches;
 * once a replacement is typed it shows each affected line before → after, and the button applies it.
 *
 * 4-6. "기본형으로 찾기" ([lemmaMode]) finds every inflected form of the word typed: "먹다" matches 먹었다,
 * 먹고, 먹는 ([LemmaSearch]). It only finds - there is nothing sensible to replace a dictionary form with -
 * and the analysis runs in the background.
 */
class SearchPane : Container() {
    var project: Project? = null
        set(value) { field = value; search() }

    var onOpenHit: (SearchHit) -> Unit = {}

    /** Called before files are rewritten, so open documents can be saved first. */
    var beforeReplace: () -> Unit = {}

    /** Called after a replace-all with the number of files changed, so open documents can be reloaded. */
    var onReplaced: (Int) -> Unit = {}

    /** Called with the search text every time the search runs, so the editor can mark the matches. */
    var onQueryChanged: (String) -> Unit = {}

    /** The text currently being searched for. */
    val queryText: String get() = query.text

    /** The text the editor should mark: the query itself, or nothing in [lemmaMode], where the matches are other forms of it. */
    val highlightText: String get() = if (lemmaMode) "" else query.text

    /** Find by dictionary form instead of by characters ("기본형으로 찾기"). */
    var lemmaMode: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            lemmaToggle.style = if (value) ButtonWidget.Style.FILLED else ButtonWidget.Style.TEXT
            lemmaToggle.label = if (value) "✓ 기본형으로 찾기" else "기본형으로 찾기"
            layout()
            search()
        }

    private val query = TextFieldWidget(accessibleLabel = "찾을 내용", placeholder = "찾기")
    private val replacement = TextFieldWidget(accessibleLabel = "바꿀 내용", placeholder = "바꾸기 (비우면 찾기만)")
    private val replaceAll = ButtonWidget("모두 바꾸기", ButtonWidget.Style.FILLED)
    private val lemmaToggle = ButtonWidget("기본형으로 찾기")
    private val work = BackgroundWork()

    /** Waits for a running lemma search (tests only; the app has an event loop to wait on). */
    internal fun awaitIdle(timeoutMillis: Long = 30_000) = work.awaitIdle(timeoutMillis)

    var hits: List<SearchHit> = emptyList()
        private set
    var previews: List<ReplacePreview> = emptyList()
        private set

    private var firstRow = 0
    private var message = ""

    init {
        listOf(query, replacement, replaceAll, lemmaToggle).forEach(::addChild)
        query.onChange = { search() }
        replacement.onChange = { search() }
        query.onSubmit = { hits.firstOrNull()?.let(onOpenHit) }
        replaceAll.onClick = ::applyReplace
        lemmaToggle.onClick = { lemmaMode = !lemmaMode }
        replaceAll.enabled = false
    }

    /** Sets the search text (e.g. from a "find" shortcut) and runs the search. */
    fun searchFor(text: String) {
        query.text = text
        search()
    }

    /**
     * The field a find shortcut should put the cursor in: the search field, or with [replace] the
     * replacement field. Its text is selected so the writer can type over it straight away.
     */
    fun shortcutField(replace: Boolean = false): TextFieldWidget =
        (if (replace) replacement else query).also { it.selectAll() }

    fun search() {
        val p = project
        val q = query.text
        val r = replacement.text
        firstRow = 0
        if (lemmaMode) {
            searchByLemma(p, q, r)
        } else {
            work.cancel()
            hits = if (p == null) emptyList() else runCatching { ProjectSearch.find(p, q) }.getOrDefault(emptyList())
            previews = if (p == null || r.isEmpty()) emptyList() else runCatching { ProjectReplace.preview(p, q, r) }.getOrDefault(emptyList())
            replaceAll.enabled = previews.isNotEmpty()
            message = when {
                q.isEmpty() -> ""
                r.isNotEmpty() -> "${previews.size}줄이 바뀝니다 (조사 자동 맞춤)"
                else -> "${hits.size}개 찾음"
            }
        }
        requestRepaint()
        onQueryChanged(q)
    }

    /** [lemmaMode] search: the hits arrive from the background; nothing can be replaced here. */
    private fun searchByLemma(p: Project?, q: String, r: String) {
        previews = emptyList()
        replaceAll.enabled = false
        hits = emptyList()
        if (p == null || q.isBlank()) {
            work.cancel()
            message = ""
            return
        }
        message = "기본형으로 찾는 중…"
        work.run({ LemmaSearch.find(p, q) }) { found ->
            hits = found
            message = when {
                found.isEmpty() -> "찾지 못했습니다 (기본형 기준)"
                r.isNotEmpty() -> "${found.size}개 찾음 (기본형 찾기에서는 바꾸지 않습니다)"
                else -> "${found.size}개 찾음 (기본형)"
            }
            requestRepaint()
        }
    }

    private fun applyReplace() {
        val p = project ?: return
        if (previews.isEmpty()) return
        beforeReplace()
        val changed = runCatching { ProjectReplace.apply(p, query.text, replacement.text) }.getOrDefault(0)
        replacement.text = ""
        search()
        message = "${changed}개 파일을 바꿨습니다"
        onReplaced(changed)
    }

    private val listTop get() = PaneStyle.HEADER_HEIGHT + 8 + 2 * (TextFieldWidget.HEIGHT + 6) + ButtonWidget.HEIGHT + 28

    override fun layout() {
        val b = bounds
        val width = (b.width - 20).coerceAtLeast(1)
        var y = b.y + PaneStyle.HEADER_HEIGHT + 8
        query.setBounds(b.x + 10, y, width, TextFieldWidget.HEIGHT)
        y += TextFieldWidget.HEIGHT + 6
        replacement.setBounds(b.x + 10, y, width, TextFieldWidget.HEIGHT)
        y += TextFieldWidget.HEIGHT + 6
        replaceAll.measure().let { replaceAll.setBounds(b.x + b.width - 10 - it.width, y, it.width, ButtonWidget.HEIGHT) }
        lemmaToggle.measure().let { lemmaToggle.setBounds(b.x + 10, y, it.width, ButtonWidget.HEIGHT) }
    }

    override fun onPaint(g: Graphics2D, localRegion: Rectangle) {
        PaneStyle.paintPanel(this, g, "찾기 · 바꾸기")
        g.font = theme.labelFont
        val fm = g.fontMetrics
        g.color = theme.outline
        g.drawString(message, 14, listTop - 12)
        val width = bounds.width - 28
        val rows: List<Pair<String, String?>> =
            if (previews.isNotEmpty()) previews.map { "${it.path.fileName}:${it.line + 1}  ${it.before}" to "→ ${it.after}" }
            else hits.map { "${it.path.fileName}:${it.line + 1}" to it.preview }
        rows.drop(firstRow).forEachIndexed { i, (first, second) ->
            val y = listTop + 16 + i * ROW_HEIGHT
            if (y > bounds.height) return
            g.color = theme.outline; g.drawString(PaneStyle.ellipsize(first, fm, width), 14, y)
            g.color = theme.onSurface; g.drawString(PaneStyle.ellipsize(second.orEmpty(), fm, width), 14, y + 18)
        }
    }

    override fun onMouseEvent(event: InputEvent.MouseInput): Boolean = when (event) {
        is InputEvent.MousePressed -> {
            val localY = event.y - bounds.y
            val hit = if (localY >= listTop && previews.isEmpty()) hits.getOrNull(firstRow + (localY - listTop) / ROW_HEIGHT) else null
            hit?.let(onOpenHit)
            hit != null
        }
        is InputEvent.Scroll -> {
            val count = if (previews.isNotEmpty()) previews.size else hits.size
            firstRow = (firstRow + PaneStyle.scrollRows(event.unitsToScroll)).coerceIn(0, (count - 1).coerceAtLeast(0))
            requestRepaint(); true
        }
        else -> false
    }

    private companion object {
        const val ROW_HEIGHT = 44
    }
}
