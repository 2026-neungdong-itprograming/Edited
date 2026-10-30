package io.github.nd2026.edited.ui.panes

import io.github.nd2026.edited.core.Parallel
import io.github.nd2026.edited.event.InputEvent
import io.github.nd2026.edited.lexer.Morphology
import io.github.nd2026.edited.project.Project
import io.github.nd2026.edited.ui.Widget
import io.github.nd2026.edited.vcs.CommitInfo
import io.github.nd2026.edited.vcs.HistoryService
import io.github.nd2026.edited.vcs.MorphemeDiff
import io.github.nd2026.edited.vcs.SentenceChange
import io.github.nd2026.edited.vcs.SentenceDiff
import io.github.nd2026.edited.vcs.WordChangeKind
import java.awt.Graphics2D
import java.awt.Rectangle
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** What a changed sentence pair is at morpheme level: the kind of edit and the content words that changed (6-7, 6-8). */
data class MorphemeNote(val kind: WordChangeKind, val words: List<Pair<String?, String?>>)

/**
 * 원고 기록: saved versions on top; picking one shows, sentence by sentence, how the open chapter
 * ([activePath]) differs between that version and now. Each changed sentence also says what kind of edit it was
 * - `[어미]`, `[조사]` or `[낱말]` - and for word changes which words ("문 → 창문"); that analysis runs in the background.
 */
class HistoryPane : Widget() {
    var project: Project? = null
        set(value) { field = value; refresh() }

    /** The chapter to compare; set by the workspace when the active document changes. */
    var activePath: Path? = null
        set(value) { field = value; compare() }

    var commits: List<CommitInfo> = emptyList()
        private set
    private var selectedCommit: CommitInfo? = null
    private var changes = emptyList<SentenceChange>()
    private var firstChange = 0

    /** Morpheme notes by index into [changes]; arrive shortly after the sentences do. */
    private var notes: Map<Int, MorphemeNote> = emptyMap()
    private val work = BackgroundWork()

    /** Waits for the running morpheme analysis (tests only; the app has an event loop to wait on). */
    internal fun awaitIdle(timeoutMillis: Long = 30_000) = work.awaitIdle(timeoutMillis)

    /** Re-reads the history; call after a save records a new version. */
    fun refresh() {
        commits = project?.let { runCatching { HistoryService(it.root).log(50) }.getOrDefault(emptyList()) }.orEmpty()
        selectedCommit = commits.firstOrNull { it.id == selectedCommit?.id }
        compare()
    }

    private fun compare() {
        val p = project
        val commit = selectedCommit
        val path = activePath
        changes = if (p == null || commit == null || path == null) emptyList() else runCatching {
            val then = HistoryService(p.root).fileAt(commit.id, path).orEmpty()
            val now = if (Files.isRegularFile(path)) Files.readString(path) else ""
            SentenceDiff.diff(then, now)
        }.getOrDefault(emptyList())
        firstChange = 0
        notes = emptyMap()
        val snapshot = changes
        if (snapshot.isEmpty()) work.cancel() else work.run({ notesFor(snapshot) }) { notes = it; requestRepaint() }
        requestRepaint()
    }

    /** The note of every changed sentence pair, analyzed in parallel; none without Kiwi. */
    private fun notesFor(changes: List<SentenceChange>): Map<Int, MorphemeNote> {
        if (!Morphology.isAvailable) return emptyMap()
        val pairs = changes.withIndex().filter { (_, c) -> c.kind == SentenceChange.Kind.CHANGED && c.before != null && c.after != null }.take(MAX_NOTES)
        return Parallel.map(pairs) { (index, c) ->
            index to MorphemeNote(MorphemeDiff.classify(c.before!!, c.after!!), MorphemeDiff.changedWords(c.before!!, c.after!!))
        }.toMap()
    }

    /** The notes shown so far, in sentence order, as the lines the pane draws (tests). */
    internal fun noteTexts(): List<String> = notes.toSortedMap().values.map(::describe)

    /** "[어미]", "[조사]", or "[낱말]  문 → 창문": a note as one line of text. */
    internal fun describe(note: MorphemeNote): String {
        val tag = when (note.kind) {
            WordChangeKind.ENDING -> "[어미]"
            WordChangeKind.PARTICLE -> "[조사]"
            WordChangeKind.WORDS -> "[낱말]"
        }
        val words = if (note.kind == WordChangeKind.WORDS) note.words.joinToString(", ") { (before, after) -> "${before ?: "∅"} → ${after ?: "∅"}" } else ""
        return if (words.isEmpty()) tag else "$tag  $words"
    }

    private val listHeight get() = (bounds.height * .4).toInt()

    override fun onPaint(g: Graphics2D, localRegion: Rectangle) {
        PaneStyle.paintPanel(this, g, "원고 기록")
        g.font = theme.labelFont
        val fm = g.fontMetrics
        val width = bounds.width - 28
        if (commits.isEmpty()) {
            g.color = theme.outline; g.drawString("저장하면(Ctrl+S) 기록이 남습니다", 14, 62); return
        }
        val listBottom = PaneStyle.HEADER_HEIGHT + listHeight
        commits.forEachIndexed { i, commit ->
            val y = FIRST_BASELINE + i * ROW_HEIGHT
            if (y > listBottom) return@forEachIndexed
            if (commit == selectedCommit) {
                g.color = PaneStyle.blend(theme.surface, theme.primary, .14f)
                g.fillRoundRect(8, y - 17, bounds.width - 16, ROW_HEIGHT - 2, 8, 8)
            }
            g.color = theme.outline; g.drawString(TIME.format(commit.time), 14, y)
            g.color = theme.onSurface; g.drawString(PaneStyle.ellipsize(commit.message, fm, width - 90), 104, y)
        }
        g.color = theme.outline
        g.drawLine(0, listBottom + 4, bounds.width, listBottom + 4)

        var y = listBottom + 26
        val header = when {
            selectedCommit == null -> "기록을 선택하면 지금 원고와 비교합니다"
            activePath == null -> "비교할 원고를 여세요"
            changes.isEmpty() -> "${activePath?.fileName}: 그때와 같습니다"
            else -> "${activePath?.fileName}: 바뀐 문장 ${changes.size}개"
        }
        g.drawString(PaneStyle.ellipsize(header, fm, width), 14, y)
        for ((offset, change) in changes.drop(firstChange).withIndex()) {
            if (y > bounds.height - 20) break
            val lines = when (change.kind) {
                SentenceChange.Kind.ADDED -> listOf("+ " + change.after to theme.primary)
                SentenceChange.Kind.REMOVED -> listOf("− " + change.before to theme.error)
                SentenceChange.Kind.CHANGED -> listOf("− " + change.before to theme.error, "+ " + change.after to theme.primary) +
                    listOfNotNull(notes[firstChange + offset]?.let { describe(it) to theme.outline })
            }
            y += 6
            for ((text, color) in lines) {
                y += 18
                g.color = color
                g.drawString(PaneStyle.ellipsize(text, fm, width), 14, y)
            }
        }
    }

    override fun onMouseEvent(event: InputEvent.MouseInput): Boolean {
        val localY = event.y - bounds.y
        val inList = localY > PaneStyle.HEADER_HEIGHT && localY <= PaneStyle.HEADER_HEIGHT + listHeight
        return when (event) {
            is InputEvent.MousePressed -> {
                val commit = if (inList) commits.getOrNull((localY - (FIRST_BASELINE - 17)) / ROW_HEIGHT) else null
                if (commit != null) { selectedCommit = commit; compare() }
                commit != null
            }
            is InputEvent.Scroll -> {
                if (inList) return false
                firstChange = (firstChange + PaneStyle.scrollRows(event.unitsToScroll)).coerceIn(0, (changes.size - 1).coerceAtLeast(0))
                requestRepaint(); true
            }
            else -> false
        }
    }

    private companion object {
        const val FIRST_BASELINE = 62
        const val ROW_HEIGHT = 24
        const val MAX_NOTES = 400
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault())
    }
}
