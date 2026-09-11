package io.github.nd2026.edited.ui

import io.github.nd2026.edited.core.TextArea
import io.github.nd2026.edited.core.TextAreaListener
import io.github.nd2026.edited.core.TextEdit
import io.github.nd2026.edited.event.InputEvent
import io.github.nd2026.edited.render.PixelMetrics
import io.github.nd2026.edited.ui.components.ContextMenuItem
import io.github.nd2026.edited.ui.components.ContextMenuWidget
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.event.InputEvent as AwtInputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.font.FontRenderContext
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** Virtualized, toolkit-native text editor with selection, clipboard and context menu support. */
class TextAreaWidget(private val textArea: TextArea = TextArea()) : Widget(), TextInputClient {
    private var scrollY = 0
    private var lastLineHeight = 20
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
            override fun onTextChanged(edit: TextEdit) = resetCaretBlink()
            override fun onCaretMoved(offset: Int) = resetCaretBlink()
            override fun onSelectionChanged(range: IntRange?) = requestRepaint()
        })
    }

    override fun onAttach() {
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
        val metrics = PixelMetrics(fm.height, fm.ascent)
        lastLineHeight = metrics.lineHeight

        val viewport = metrics.viewportFor(scrollY, bounds.height, textArea.lineCount)
        val visibleRange = textArea.visibleLineRange(viewport)
        if (visibleRange.isEmpty()) return

        var line = visibleRange.first
        for (lineText in textArea.visibleText(viewport).split('\n')) {
            if (line !in visibleRange) break
            if (composedText.isNotEmpty() && line == compositionLine()) {
                paintCompositionLine(g, metrics, line, lineText)
            } else {
                paintSelection(g, metrics, line, lineText)
                g.color = t.onSurface
                g.drawString(lineText, TEXT_INSET, metrics.baselineOf(line) - scrollY)
            }
            line++
        }

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
            !char.isISOControl() || char == '\n' -> textArea.typeAtCaret(char.toString())
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
                else -> false
            }
        }

        val extend = event.modifiers and AwtInputEvent.SHIFT_DOWN_MASK != 0
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
        is InputEvent.Scroll -> {
            val maxScroll = (textArea.lineCount * lastLineHeight - bounds.height).coerceAtLeast(0)
            scrollY = (scrollY + (event.unitsToScroll * lastLineHeight).toInt()).coerceIn(0, maxScroll)
            requestRepaint()
            true
        }
        else -> false
    }

    private fun offsetAt(rootX: Int, rootY: Int): Int {
        val line = ((rootY - bounds.y + scrollY) / lastLineHeight)
            .coerceIn(0, textArea.lineCount - 1)
        val text = textArea.lineText(line)
        val targetX = (rootX - bounds.x - TEXT_INSET).coerceAtLeast(0)
        val context = FontRenderContext(null, true, true)
        var previousWidth = 0.0
        for (column in text.indices) {
            val width = theme.monospaceFont.getStringBounds(text, 0, column + 1, context).width
            if (targetX < ((previousWidth + width) / 2.0).roundToInt()) {
                return textArea.lineStart(line) + column
            }
            previousWidth = width
        }
        return textArea.lineEnd(line)
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
        else if (textArea.caret > 0) textArea.delete(textArea.caret - 1, textArea.caret)
    }

    private fun deleteForward(): Boolean {
        val selection = textArea.selectionRange()?.takeUnless(IntRange::isEmpty)
        if (selection != null) textArea.delete(selection.first, selection.last + 1)
        else if (textArea.caret < textArea.length) textArea.delete(textArea.caret, textArea.caret + 1)
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
            textArea.typeAtCaret(committedText)
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
        val y = line * lastLineHeight - scrollY
        return Rectangle(bounds.x + x, bounds.y + y, 1, lastLineHeight)
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
        private const val CARET_BLINK_MILLIS = 530L
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
