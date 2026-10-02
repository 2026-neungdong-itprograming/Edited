package io.github.nd2026.edited.ui

import io.github.nd2026.edited.core.TextArea
import io.github.nd2026.edited.core.TextAreaListener
import io.github.nd2026.edited.core.TextEdit
import io.github.nd2026.edited.event.InputEvent
import io.github.nd2026.edited.lexer.DefaultMorphemeAnalyzer
import io.github.nd2026.edited.lexer.MorphemeAnalyzer
import io.github.nd2026.edited.lexer.MorphemeIndex
import io.github.nd2026.edited.lexer.MorphemeIndexListener
import io.github.nd2026.edited.lexer.Morpheme
import io.github.nd2026.edited.render.PixelMetrics
import io.github.nd2026.edited.ui.components.ContextMenuItem
import io.github.nd2026.edited.ui.components.ContextMenuWidget
import io.github.nd2026.edited.ui.components.withAlpha
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.event.InputEvent as AwtInputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.font.FontRenderContext
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** Virtualized, toolkit-native text editor with selection, clipboard and context menu support. */
class TextAreaWidget(
    val textArea: TextArea = TextArea(),
    private val analyzer: MorphemeAnalyzer = DefaultMorphemeAnalyzer,
) : Widget(), TextInputClient {
    private var morphemeIndex: MorphemeIndex? = null

    /** The sentence under the mouse (document offsets, end exclusive), the only text that gets POS colors. */
    private var hoveredSentence: IntRange? = null

    var scrollY = 0
        private set
    var scrollX = 0
        private set

    /** Widest line painted so far; the horizontal scroll range. Exact lengths of unseen lines are never computed. */
    private var contentWidth = 0
    private val scrollListeners = CopyOnWriteArrayList<() -> Unit>()

    /** Pixel height of one text line for the current theme font; stable before the first paint. */
    val lineHeight: Int get() = EditorFontMetrics.of(theme.monospaceFont).height
    private var selectionDrag = false
    private var contextMenu: ContextMenuWidget? = null
    private var composedText = ""
    private var compositionCaret = 0
    private var compositionAnchor: Int? = null
    private var compositionReplacementEnd: Int? = null

    @Volatile
    private var caretVisible = true
    private var blinkTask: ScheduledFuture<*>? = null

    init {
        focusable = true
        semantics = Semantics(
            role = Semantics.Role.TEXT_FIELD,
            name = "원고 편집기",
            actions = setOf(Semantics.Action.FOCUS, Semantics.Action.SET_VALUE),
        )
        textArea.addListener(object : TextAreaListener {
            override fun onTextChanged(edit: TextEdit) {
                hoveredSentence = null // offsets are stale after an edit; the next mouse move recomputes
                resetCaretBlink()
            }
            override fun onCaretMoved(offset: Int) {
                revealCaret()
                resetCaretBlink()
            }
            override fun onSelectionChanged(range: IntRange?) = requestRepaint()
        })
    }

    override fun onAttach() {
        if (morphemeIndex == null) {
            morphemeIndex = MorphemeIndex(textArea, analyzer).also { index ->
                index.addListener(object : MorphemeIndexListener {
                    override fun onLineAnalyzed(line: Int, morphemes: List<Morpheme>) = requestRepaint()
                })
            }
        }
        if (blinkTask != null) return
        blinkTask = caretClock.scheduleAtFixedRate({
            if (focused) {
                caretVisible = !caretVisible
                requestRepaint()
            } else {
                caretVisible = true
            }
        }, CARET_BLINK_MILLIS, CARET_BLINK_MILLIS, TimeUnit.MILLISECONDS)
    }

    override fun onDetach() {
        morphemeIndex?.close()
        morphemeIndex = null
        blinkTask?.cancel(false)
        blinkTask = null
        dismissContextMenu()
        clearComposition()
    }

    override fun onPaint(g: Graphics2D, localRegion: Rectangle) {
        val t = theme
        g.color = t.surface
        g.fillRect(0, 0, bounds.width, bounds.height)
        g.font = t.monospaceFont
        val fm = g.fontMetrics
        val editorMetrics = EditorFontMetrics.of(t.monospaceFont)
        val metrics = PixelMetrics(editorMetrics.height, editorMetrics.ascent)

        val viewport = metrics.viewportFor(scrollY, bounds.height, textArea.lineCount)
        val visibleRange = textArea.visibleLineRange(viewport)
        if (visibleRange.isEmpty()) return

        // Text lives in a horizontally scrolled coordinate space; the popup and bars stay in the viewport's.
        val viewGraphics = g
        val g = viewGraphics.create() as Graphics2D
        g.translate(-scrollX, 0)
        var widest = 0
        var line = visibleRange.first
        for (lineText in textArea.visibleText(viewport).split('\n')) {
            if (line !in visibleRange) break
            if (composedText.isNotEmpty() && line == compositionLine()) {
                paintCompositionLine(g, metrics, line, lineText)
                widest = maxOf(widest, fm.stringWidth(lineText) + fm.stringWidth(composedText))
            } else {
                paintSelection(g, metrics, line, lineText)
                paintLine(g, metrics, line, lineText)
                widest = maxOf(widest, fm.stringWidth(lineText))
            }
            line++
        }
        updateContentWidth(widest + 2 * TEXT_INSET)

        if (focused && caretVisible) {
            val caretLine = compositionLine() ?: textArea.lineOf(textArea.caret)
            if (caretLine in visibleRange) {
                val caretX = if (composedText.isNotEmpty()) {
                    compositionCaretX(fm::stringWidth)
                } else {
                    val caretCol = textArea.caret - textArea.lineStart(caretLine)
                    TEXT_INSET + fm.stringWidth(textArea.lineText(caretLine).take(caretCol))
                }
                val caretY = metrics.topOf(caretLine) - scrollY
                g.color = t.primary
                g.drawLine(caretX, caretY + 2, caretX, caretY + metrics.lineHeight - 2)
            }
        }
        g.dispose()
        paintMorphemePopup(viewGraphics, metrics, visibleRange)
        paintHorizontalScrollThumb(viewGraphics)
    }

    private fun updateContentWidth(visibleWidest: Int) {
        // Grow-only, so the thumb doesn't jitter while scrolling; shrinks when the view is reset.
        if (visibleWidest > contentWidth) contentWidth = visibleWidest
        val max = maxScrollX()
        if (scrollX > max) scrollX = max
    }

    private fun paintHorizontalScrollThumb(g: Graphics2D) {
        val max = maxScrollX()
        if (max <= 0) return
        val track = bounds.width
        val thumb = (track.toLong() * bounds.width / contentWidth).toInt().coerceAtLeast(24)
        val x = ((track - thumb).toLong() * scrollX / max).toInt()
        g.color = theme.outline.withAlpha(110)
        g.fillRoundRect(x, bounds.height - 5, thumb, 4, 4, 4)
    }

    private fun maxScrollX(): Int = (contentWidth - bounds.width).coerceAtLeast(0)
    private fun maxScrollY(): Int = (textArea.lineCount * lineHeight - bounds.height).coerceAtLeast(0)

    fun addScrollListener(listener: () -> Unit) {
        scrollListeners.add(listener)
    }

    fun removeScrollListener(listener: () -> Unit) {
        scrollListeners.remove(listener)
    }

    /** Scrolls to the given pixel offsets (clamped); notifies the gutter and stripe only when something moved. */
    fun scrollTo(x: Int = scrollX, y: Int = scrollY) {
        val nx = x.coerceIn(0, maxScrollX())
        val ny = y.coerceIn(0, maxScrollY())
        if (nx == scrollX && ny == scrollY) return
        scrollX = nx
        scrollY = ny
        requestRepaint()
        for (l in scrollListeners) l()
    }

    /** Scrolls so that [line] is the first visible line. */
    fun scrollToLine(line: Int) = scrollTo(y = line.coerceIn(0, textArea.lineCount - 1) * lineHeight)

    /** Scrolls the minimum distance that brings the caret fully into view (with a small horizontal margin). */
    fun revealCaret() {
        if (bounds.width <= 0 || bounds.height <= 0) return
        val lh = lineHeight
        val caretLine = compositionLine() ?: textArea.lineOf(textArea.caret)
        val top = caretLine * lh
        var y = scrollY
        if (top < y) y = top else if (top + lh > y + bounds.height) y = top + lh - bounds.height

        val column = (textArea.caret - textArea.lineStart(caretLine)).coerceAtLeast(0)
        val caretX = TEXT_INSET + textWidth(textArea.lineText(caretLine).take(column))
        var x = scrollX
        val margin = (bounds.width / 8).coerceIn(8, 48)
        if (caretX - margin < x) x = caretX - margin
        else if (caretX + margin > x + bounds.width) x = caretX + margin - bounds.width
        // The caret can sit beyond the widest painted line (typing at a line's end), so widen the range first.
        if (caretX + TEXT_INSET > contentWidth) contentWidth = caretX + TEXT_INSET
        scrollTo(x, y)
    }

    /** Plain text, except the hovered sentence, whose chars are colored by their morpheme's part of speech. */
    private fun paintLine(g: Graphics2D, metrics: PixelMetrics, line: Int, lineText: String) {
        val baseline = metrics.baselineOf(line) - scrollY
        val lineStart = textArea.lineStart(line)
        val sentence = hoveredSentence
        val morphemes = morphemeIndex?.morphemesForLine(line)
        val from = ((sentence?.first ?: 0) - lineStart).coerceIn(0, lineText.length)
        val to = (((sentence?.last ?: -1) + 1) - lineStart).coerceIn(from, lineText.length)
        if (sentence == null || morphemes.isNullOrEmpty() || from == to) {
            g.color = theme.onSurface
            g.drawString(lineText, TEXT_INSET, baseline)
            return
        }

        // Morphemes can share a source span (e.g. contracted syllables); the first one wins.
        val colors = arrayOfNulls<Color>(lineText.length)
        for (m in morphemes) {
            val color = PosHighlight.colorFor(theme, m.partOfSpeech)
            for (i in (m.start - lineStart).coerceAtLeast(from) until (m.end - lineStart).coerceAtMost(to)) {
                if (colors[i] == null) colors[i] = color
            }
        }

        var runStart = 0
        while (runStart < lineText.length) {
            val color = colors[runStart] ?: theme.onSurface
            var runEnd = runStart + 1
            while (runEnd < lineText.length && (colors[runEnd] ?: theme.onSurface) == color) runEnd++
            g.color = color
            g.drawString(
                lineText.substring(runStart, runEnd),
                TEXT_INSET + g.fontMetrics.stringWidth(lineText.take(runStart)),
                baseline,
            )
            runStart = runEnd
        }
    }

    /** A small card next to the hovered sentence listing its morphemes as `surface/TAG`. */
    private fun paintMorphemePopup(g: Graphics2D, metrics: PixelMetrics, visibleRange: IntRange) {
        val sentence = hoveredSentence ?: return
        val line = textArea.lineOf(sentence.first)
        if (line !in visibleRange || composedText.isNotEmpty()) return
        val morphemes = morphemeIndex?.morphemesForLine(line)
            ?.filter { it.start >= sentence.first && it.start <= sentence.last } ?: return
        if (morphemes.isEmpty()) return

        val fm = g.fontMetrics
        val padding = 8
        val gap = fm.stringWidth("  ")
        val maxWidth = (bounds.width - 2 * TEXT_INSET).coerceAtLeast(120)
        val rowHeight = fm.height + 2

        // Lay out tokens left to right, wrapping when a row would exceed the card width.
        class Token(val surface: String, val tag: String, val color: Color, val x: Int, val row: Int)
        val tokens = ArrayList<Token>()
        var x = 0
        var row = 0
        var widest = 0
        for (m in morphemes) {
            val tag = "/${m.partOfSpeech}"
            val width = fm.stringWidth(m.surface) + fm.stringWidth(tag)
            if (x > 0 && x + width > maxWidth - 2 * padding) { x = 0; row++ }
            tokens += Token(m.surface, tag, PosHighlight.colorFor(theme, m.partOfSpeech), x, row)
            x += width + gap
            widest = maxOf(widest, x - gap)
        }
        val cardWidth = widest + 2 * padding
        val cardHeight = (row + 1) * rowHeight + 2 * padding
        val cardX = TEXT_INSET.coerceAtMost((bounds.width - cardWidth).coerceAtLeast(0))
        val lineTop = metrics.topOf(line) - scrollY
        val below = lineTop + metrics.lineHeight + 4
        val cardY = if (below + cardHeight <= bounds.height) below else (lineTop - cardHeight - 4).coerceAtLeast(0)

        g.color = theme.surface
        g.fillRoundRect(cardX, cardY, cardWidth, cardHeight, 10, 10)
        g.color = theme.outline
        g.drawRoundRect(cardX, cardY, cardWidth - 1, cardHeight - 1, 10, 10)
        for (t in tokens) {
            val tx = cardX + padding + t.x
            val baseline = cardY + padding + t.row * rowHeight + fm.ascent
            g.color = t.color
            g.drawString(t.surface, tx, baseline)
            g.color = theme.outline
            g.drawString(t.tag, tx + fm.stringWidth(t.surface), baseline)
        }
    }

    /** The sentence containing [offset] within its line, or null over blank space. */
    private fun sentenceAt(offset: Int): IntRange? {
        val line = textArea.lineOf(offset)
        val text = textArea.lineText(line)
        if (text.isBlank()) return null
        val lineStart = textArea.lineStart(line)
        val column = (offset - lineStart).coerceIn(0, text.length - 1)
        if (text[column].isWhitespace()) return null

        var start = 0
        var i = 0
        while (i < text.length) {
            var end = i
            while (end < text.length && text[end] !in SENTENCE_END) end++
            if (end < text.length) {
                end++
                while (end < text.length && (text[end] in SENTENCE_END || text[end] in SENTENCE_CLOSERS)) end++
            }
            if (column < end) {
                while (start < end && text[start].isWhitespace()) start++
                return (lineStart + start) until (lineStart + end)
            }
            i = end
            start = end
        }
        return null
    }

    private fun updateHoveredSentence(sentence: IntRange?) {
        if (sentence == hoveredSentence) return
        hoveredSentence = sentence
        requestRepaint()
    }

    private fun paintCompositionLine(g: Graphics2D, metrics: PixelMetrics, line: Int, lineText: String) {
        val anchor = compositionAnchor ?: return
        val lineStart = textArea.lineStart(line)
        val fromColumn = (anchor - lineStart).coerceIn(0, lineText.length)
        val toColumn = ((compositionReplacementEnd ?: anchor) - lineStart).coerceIn(fromColumn, lineText.length)
        val prefix = lineText.take(fromColumn)
        val suffix = lineText.drop(toColumn)
        val baseline = metrics.baselineOf(line) - scrollY
        val compositionX = TEXT_INSET + g.fontMetrics.stringWidth(prefix)

        g.color = theme.onSurface
        g.drawString(prefix + composedText + suffix, TEXT_INSET, baseline)
        g.color = theme.primary
        val compositionWidth = g.fontMetrics.stringWidth(composedText).coerceAtLeast(2)
        g.drawLine(compositionX, baseline + 2, compositionX + compositionWidth, baseline + 2)
    }

    private fun paintSelection(g: Graphics2D, metrics: PixelMetrics, line: Int, lineText: String) {
        val selection = textArea.selectionRange()?.takeUnless(IntRange::isEmpty) ?: return
        val lineStart = textArea.lineStart(line)
        val lineEnd = textArea.lineEnd(line)
        val selectedStart = selection.first.coerceAtLeast(lineStart)
        val selectedEndExclusive = (selection.last + 1).coerceAtMost(lineEnd)
        if (selectedStart >= selectedEndExclusive) return

        val fm = g.fontMetrics
        val fromColumn = selectedStart - lineStart
        val toColumn = (selectedEndExclusive - lineStart).coerceAtMost(lineText.length)
        val x = TEXT_INSET + fm.stringWidth(lineText.take(fromColumn))
        val width = fm.stringWidth(lineText.substring(fromColumn, toColumn)).coerceAtLeast(2)
        val y = metrics.topOf(line) - scrollY
        g.color = Color(theme.primary.red, theme.primary.green, theme.primary.blue, 56)
        g.fillRect(x, y, width, metrics.lineHeight)
    }

    override fun onKeyEvent(event: InputEvent.KeyInput): Boolean = when (event) {
        is InputEvent.KeyTyped -> handleTyped(event.char)
        is InputEvent.KeyPressed -> handlePressed(event)
        is InputEvent.KeyReleased -> false
    }

    private fun handleTyped(char: Char): Boolean {
        when {
            char == '\b' -> deleteBackward()
            !char.isISOControl() || char == '\n' -> textArea.typeAtCaret(char.toString(), coalesce = true)
            else -> return false
        }
        resetCaretBlink()
        return true
    }

    private fun handlePressed(event: InputEvent.KeyPressed): Boolean {
        if (event.modifiers and menuShortcutMask != 0) {
            return when (event.keyCode) {
                KeyEvent.VK_C -> copySelection()
                KeyEvent.VK_X -> cutSelection()
                KeyEvent.VK_V -> pasteClipboard()
                KeyEvent.VK_A -> selectAll()
                KeyEvent.VK_Z -> if (event.modifiers and AwtInputEvent.SHIFT_DOWN_MASK != 0) redo() else undo()
                KeyEvent.VK_Y -> redo()
                else -> false
            }
        }

        val extend = event.modifiers and AwtInputEvent.SHIFT_DOWN_MASK != 0
        if (event.keyCode in NAVIGATION_KEYS) textArea.breakUndoGroup()
        when (event.keyCode) {
            KeyEvent.VK_LEFT -> textArea.moveCaretTo(textArea.caret - 1, extend)
            KeyEvent.VK_RIGHT -> textArea.moveCaretTo(textArea.caret + 1, extend)
            KeyEvent.VK_UP -> moveCaretVertically(-1, extend)
            KeyEvent.VK_DOWN -> moveCaretVertically(1, extend)
            KeyEvent.VK_HOME -> textArea.moveCaretTo(textArea.lineStart(textArea.lineOf(textArea.caret)), extend)
            KeyEvent.VK_END -> textArea.moveCaretTo(textArea.lineEnd(textArea.lineOf(textArea.caret)), extend)
            KeyEvent.VK_DELETE -> deleteForward()
            else -> return false
        }
        resetCaretBlink()
        return true
    }

    override fun onMouseEvent(event: InputEvent.MouseInput): Boolean = when (event) {
        is InputEvent.MousePressed -> when (event.button) {
            MouseEvent.BUTTON1 -> {
                dismissContextMenu()
                textArea.breakUndoGroup()
                textArea.moveCaretTo(offsetAt(event.x, event.y))
                selectionDrag = true
                resetCaretBlink()
                true
            }
            MouseEvent.BUTTON3 -> {
                val offset = offsetAt(event.x, event.y)
                val selection = textArea.selectionRange()
                if (selection == null || selection.isEmpty() || offset !in selection) textArea.moveCaretTo(offset)
                showContextMenu(event.x, event.y)
                true
            }
            else -> false
        }
        is InputEvent.MouseDragged -> {
            if (!selectionDrag) false else {
                textArea.moveCaretTo(offsetAt(event.x, event.y), extendSelection = true)
                true
            }
        }
        is InputEvent.MouseReleased -> {
            val wasDragging = selectionDrag
            selectionDrag = false
            wasDragging
        }
        is InputEvent.MouseCancelled -> {
            selectionDrag = false
            true
        }
        is InputEvent.MouseMoved -> {
            updateHoveredSentence(sentenceAt(offsetAt(event.x, event.y)))
            false
        }
        is InputEvent.MouseExited -> {
            updateHoveredSentence(null)
            false
        }
        is InputEvent.Scroll -> {
            val delta = (event.unitsToScroll * lineHeight).toInt()
            if (event.modifiers and AwtInputEvent.SHIFT_DOWN_MASK != 0) scrollTo(x = scrollX + delta)
            else scrollTo(y = scrollY + delta)
            true
        }
        else -> false
    }

    private fun offsetAt(rootX: Int, rootY: Int): Int {
        val line = ((rootY - bounds.y + scrollY) / lineHeight)
            .coerceIn(0, textArea.lineCount - 1)
        val text = textArea.lineText(line)
        val targetX = (rootX - bounds.x - TEXT_INSET + scrollX).coerceAtLeast(0)
        return textArea.lineStart(line) + columnAt(text, targetX)
    }

    /** The caret column nearest [targetX]: binary search over prefix widths instead of measuring every column. */
    private fun columnAt(text: String, targetX: Int): Int {
        var low = 0
        var high = text.length
        while (low < high) {
            val mid = (low + high) ushr 1
            // Boundary between column `mid` and `mid + 1` is the midpoint of that character.
            val before = textWidth(text.substring(0, mid)).toDouble()
            val after = textWidth(text.substring(0, mid + 1)).toDouble()
            if (targetX < (before + after) / 2.0) high = mid else low = mid + 1
        }
        return low
    }

    private fun moveCaretVertically(delta: Int, extend: Boolean) {
        val currentLine = textArea.lineOf(textArea.caret)
        val column = textArea.caret - textArea.lineStart(currentLine)
        val targetLine = (currentLine + delta).coerceIn(0, textArea.lineCount - 1)
        textArea.moveCaretTo(textArea.lineStart(targetLine) + column.coerceAtMost(textArea.lineText(targetLine).length), extend)
    }

    private fun deleteBackward() {
        val selection = textArea.selectionRange()?.takeUnless(IntRange::isEmpty)
        if (selection != null) textArea.delete(selection.first, selection.last + 1)
        else if (textArea.caret > 0) textArea.delete(textArea.caret - 1, textArea.caret, coalesce = true)
    }

    private fun deleteForward(): Boolean {
        val selection = textArea.selectionRange()?.takeUnless(IntRange::isEmpty)
        if (selection != null) textArea.delete(selection.first, selection.last + 1)
        else if (textArea.caret < textArea.length) textArea.delete(textArea.caret, textArea.caret + 1, coalesce = true)
        resetCaretBlink()
        return true
    }

    private fun selectedText(): String? {
        val selection = textArea.selectionRange()?.takeUnless(IntRange::isEmpty) ?: return null
        return textArea.textInRange(selection.first, selection.last + 1)
    }

    private fun copySelection(): Boolean = selectedText()?.let(ClipboardService::writeText) ?: false

    private fun cutSelection(): Boolean {
        val selection = textArea.selectionRange()?.takeUnless(IntRange::isEmpty) ?: return false
        if (!ClipboardService.writeText(textArea.textInRange(selection.first, selection.last + 1))) return false
        textArea.delete(selection.first, selection.last + 1)
        return true
    }

    private fun pasteClipboard(): Boolean {
        val text = ClipboardService.readText() ?: return false
        textArea.typeAtCaret(text)
        resetCaretBlink()
        return true
    }

    /** While an IME composition is open the buffer lacks the composed text, so undo waits until it is committed. */
    private fun undo(): Boolean = composedText.isNotEmpty() || textArea.undo().also { resetCaretBlink() }

    private fun redo(): Boolean = composedText.isNotEmpty() || textArea.redo().also { resetCaretBlink() }

    private fun selectAll(): Boolean {
        textArea.moveCaretTo(0)
        textArea.moveCaretTo(textArea.length, extendSelection = true)
        resetCaretBlink()
        return true
    }

    private fun showContextMenu(rootX: Int, rootY: Int) {
        dismissContextMenu()
        val overlay = findOverlayHost() ?: return
        lateinit var menu: ContextMenuWidget
        val hasSelection = selectedText() != null
        menu = ContextMenuWidget(
            items = listOf(
                ContextMenuItem("잘라내기", shortcutLabel("X"), hasSelection) { cutSelection() },
                ContextMenuItem("복사", shortcutLabel("C"), hasSelection) { copySelection() },
                ContextMenuItem("붙여넣기", shortcutLabel("V"), ClipboardService.readText() != null) { pasteClipboard() },
                ContextMenuItem("전체 선택", shortcutLabel("A"), textArea.length > 0) { selectAll() },
            ),
            onDismiss = { overlay.removeOverlay(menu); if (contextMenu === menu) contextMenu = null },
        )
        val width = 210
        val height = ContextMenuWidget.preferredHeight(4)
        val x = rootX.coerceIn(overlay.bounds.x + 4, (overlay.bounds.x + overlay.bounds.width - width - 4).coerceAtLeast(overlay.bounds.x + 4))
        val y = rootY.coerceIn(overlay.bounds.y + 4, (overlay.bounds.y + overlay.bounds.height - height - 4).coerceAtLeast(overlay.bounds.y + 4))
        menu.setBounds(x, y, width, height)
        contextMenu = menu
        overlay.showOverlay(menu, dismissOnOutside = true)
    }

    private fun dismissContextMenu() {
        val menu = contextMenu ?: return
        findOverlayHost()?.removeOverlay(menu)
        contextMenu = null
    }

    private fun findOverlayHost(): OverlayHostWidget? {
        var node = parent
        while (node != null) {
            if (node is OverlayHostWidget) return node
            node = node.parent
        }
        return null
    }

    private fun resetCaretBlink() {
        caretVisible = true
        requestRepaint()
    }

    override fun updateComposition(
        committedText: String,
        composedText: String,
        caretInComposition: Int,
    ): Boolean {
        if (committedText.isNotEmpty()) {
            // One IME commit is one undo unit (a replaced selection included); commits of the same word merge.
            textArea.typeAtCaret(committedText, coalesce = true)
            compositionAnchor = null
            compositionReplacementEnd = null
        }

        if (composedText.isNotEmpty() && compositionAnchor == null) {
            val selection = textArea.selectionRange()?.takeUnless(IntRange::isEmpty)
            compositionAnchor = selection?.first ?: textArea.caret
            compositionReplacementEnd = selection?.let { it.last + 1 } ?: textArea.caret
        }

        this.composedText = composedText
        compositionCaret = caretInComposition.coerceIn(0, composedText.length)
        if (composedText.isEmpty()) {
            compositionAnchor = null
            compositionReplacementEnd = null
        }
        resetCaretBlink()
        return true
    }

    override fun updateCompositionCaret(caretInComposition: Int) {
        compositionCaret = caretInComposition.coerceIn(0, composedText.length)
        resetCaretBlink()
    }

    override fun inputMethodTextLocation(offsetInComposition: Int): Rectangle {
        val line = compositionLine() ?: textArea.lineOf(textArea.caret)
        val offset = if (composedText.isEmpty()) 0 else offsetInComposition.coerceIn(0, composedText.length)
        val x = if (composedText.isEmpty()) {
            val column = textArea.caret - textArea.lineStart(line)
            TEXT_INSET + textWidth(textArea.lineText(line).take(column))
        } else {
            compositionCaretX { textWidth(it) } - textWidth(composedText.take(compositionCaret)) +
                textWidth(composedText.take(offset))
        }
        val y = line * lineHeight - scrollY
        return Rectangle(bounds.x + x - scrollX, bounds.y + y, 1, lineHeight)
    }

    override fun inputMethodInsertOffset(): Int = compositionAnchor ?: textArea.caret

    override fun committedText(beginIndex: Int, endIndex: Int): String {
        require(beginIndex in 0..endIndex && endIndex <= textArea.length) { "invalid committed text range" }
        return textArea.textInRange(beginIndex, endIndex)
    }

    override fun committedTextLength(): Int = textArea.length

    override fun selectedTextForInputMethod(): String? = selectedText()

    private fun compositionLine(): Int? = compositionAnchor?.let(textArea::lineOf)

    private fun compositionCaretX(widthOf: (String) -> Int): Int {
        val anchor = compositionAnchor ?: return TEXT_INSET
        val line = textArea.lineOf(anchor)
        val prefix = textArea.lineText(line).take(anchor - textArea.lineStart(line))
        return TEXT_INSET + widthOf(prefix) + widthOf(composedText.take(compositionCaret))
    }

    private fun textWidth(text: String): Int = theme.monospaceFont
        .getStringBounds(text, FontRenderContext(null, true, true)).width.roundToInt()

    private fun clearComposition() {
        composedText = ""
        compositionCaret = 0
        compositionAnchor = null
        compositionReplacementEnd = null
    }

    private fun shortcutLabel(key: String): String = if (menuShortcutMask == AwtInputEvent.META_DOWN_MASK) "⌘$key" else "Ctrl+$key"

    companion object {
        private const val TEXT_INSET = 8
        private const val SENTENCE_END = ".!?。…"
        private const val SENTENCE_CLOSERS = "\"'”’」』)]"
        private const val CARET_BLINK_MILLIS = 530L
        private val NAVIGATION_KEYS = setOf(
            KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT, KeyEvent.VK_UP, KeyEvent.VK_DOWN, KeyEvent.VK_HOME, KeyEvent.VK_END,
        )
        private val menuShortcutMask = try {
            Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx
        } catch (_: Exception) {
            AwtInputEvent.CTRL_DOWN_MASK
        }
        private val caretClock = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "texted-caret-clock").apply { isDaemon = true }
        }
    }
}
