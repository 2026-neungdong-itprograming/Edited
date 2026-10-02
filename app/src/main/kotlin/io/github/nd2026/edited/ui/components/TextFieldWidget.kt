package io.github.nd2026.edited.ui.components

import io.github.nd2026.edited.core.TextArea
import io.github.nd2026.edited.core.TextAreaListener
import io.github.nd2026.edited.core.TextEdit
import io.github.nd2026.edited.event.InputEvent
import io.github.nd2026.edited.ui.ClipboardService
import io.github.nd2026.edited.ui.Semantics
import io.github.nd2026.edited.ui.TextInputClient
import io.github.nd2026.edited.ui.Widget
import io.github.nd2026.edited.ui.layout.Constraints
import io.github.nd2026.edited.ui.layout.IntSize
import java.awt.BasicStroke
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.event.InputEvent as AwtInputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.font.FontRenderContext
import kotlin.math.roundToInt

/**
 * Single-line Material outlined text field. Editing state lives in a [TextArea] (so selection
 * and caret behave like the manuscript editor); this widget only filters out line breaks and
 * paints a horizontally scrolling line, including IME composition for Hangul input.
 */
class TextFieldWidget(
    initialText: String = "",
    accessibleLabel: String = "",
    var placeholder: String = "",
) : Widget(), TextInputClient {

    var onSubmit: () -> Unit = {}
    var onChange: (String) -> Unit = {}

    /** Draws the error outline. */
    var invalid: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            requestRepaint()
        }

    private val area = TextArea(sanitize(initialText))
    private var scrollX = 0
    private var dragging = false
    private var composedText = ""
    private var compositionCaret = 0
    private var compositionAnchor: Int? = null
    private var compositionReplacementEnd: Int? = null

    var text: String
        get() = area.snapshot()
        set(value) {
            area.delete(0, area.length)
            area.insert(0, sanitize(value))
            area.clearSelection()
        }

    init {
        focusable = true
        semantics = Semantics(
            role = Semantics.Role.TEXT_FIELD,
            name = accessibleLabel,
            actions = setOf(Semantics.Action.FOCUS, Semantics.Action.SET_VALUE),
        )
        area.moveCaretTo(area.length)
        area.addListener(object : TextAreaListener {
            override fun onTextChanged(edit: TextEdit) {
                requestRepaint()
                onChange(area.snapshot())
            }

            override fun onCaretMoved(offset: Int) = requestRepaint()
            override fun onSelectionChanged(range: IntRange?) = requestRepaint()
        })
    }

    override fun onMeasure(constraints: Constraints): IntSize = constraints.constrain(IntSize(240, HEIGHT))

    override fun onPaint(g: Graphics2D, localRegion: Rectangle) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        val t = theme
        val w = bounds.width
        val h = bounds.height
        g.color = t.surface
        g.fillRoundRect(0, 0, w, h, RADIUS, RADIUS)
        g.color = when {
            !enabled -> t.outline.withAlpha(97)
            invalid -> t.error
            focused -> t.primary
            hovered -> t.onSurface
            else -> t.outline
        }
        g.stroke = BasicStroke(if (focused || invalid) 2f else 1f)
        g.drawRoundRect(1, 1, w - 3, h - 3, RADIUS, RADIUS)
        g.stroke = BasicStroke(1f)

        g.font = t.bodyFont
        val fm = g.fontMetrics
        val baseline = (h + fm.ascent - fm.descent) / 2
        val display = displayText()
        val caretIndex = compositionAnchor?.let { it + compositionCaret } ?: area.caret
        val caretX = textWidth(display.take(caretIndex))

        val innerWidth = w - 2 * PADDING
        if (caretX - scrollX > innerWidth - 2) scrollX = caretX - innerWidth + 2
        if (caretX - scrollX < 0) scrollX = caretX
        scrollX = scrollX.coerceAtLeast(0)

        val oldClip = g.clip
        g.clipRect(PADDING, 0, innerWidth, h)
        val originX = PADDING - scrollX

        if (display.isEmpty()) {
            g.color = t.outline
            g.drawString(placeholder, PADDING, baseline)
        } else {
            val selection = if (compositionAnchor == null) area.selectionRange()?.takeUnless(IntRange::isEmpty) else null
            if (selection != null) {
                val x1 = originX + textWidth(display.take(selection.first))
                val x2 = originX + textWidth(display.take(selection.last + 1))
                g.color = t.primary.withAlpha(56)
                g.fillRect(x1, (h - fm.height) / 2, x2 - x1, fm.height)
            }
            g.color = if (enabled) t.onSurface else t.onSurface.withAlpha(97)
            g.drawString(display, originX, baseline)
            compositionAnchor?.let { anchor ->
                val x1 = originX + textWidth(display.take(anchor))
                val x2 = originX + textWidth(display.take(anchor + composedText.length))
                g.color = t.primary
                g.drawLine(x1, baseline + 2, x2.coerceAtLeast(x1 + 2), baseline + 2)
            }
        }
        if (focused && enabled) {
            g.color = t.primary
            g.drawLine(originX + caretX, (h - fm.height) / 2, originX + caretX, (h + fm.height) / 2)
        }
        g.clip = oldClip
    }

    /** The committed text with any in-progress IME composition spliced in. */
    private fun displayText(): String {
        val anchor = compositionAnchor ?: return area.snapshot()
        val committed = area.snapshot()
        val end = (compositionReplacementEnd ?: anchor).coerceIn(anchor, committed.length)
        return committed.take(anchor) + composedText + committed.drop(end)
    }

    // --- keyboard ---------------------------------------------------------------------------

    override fun onKeyEvent(event: InputEvent.KeyInput): Boolean = when (event) {
        is InputEvent.KeyTyped -> handleTyped(event.char)
        is InputEvent.KeyPressed -> handlePressed(event)
        is InputEvent.KeyReleased -> false
    }

    private fun handleTyped(char: Char): Boolean = when {
        char == '\b' -> { deleteBackward(); true }
        char == '\n' || char == '\r' -> true // Enter is handled on key press
        char.isISOControl() -> false
        else -> { area.typeAtCaret(char.toString()); true }
    }

    private fun handlePressed(event: InputEvent.KeyPressed): Boolean {
        if (event.modifiers and SHORTCUT_MASK != 0) {
            return when (event.keyCode) {
                KeyEvent.VK_A -> {
                    area.moveCaretTo(0)
                    area.moveCaretTo(area.length, extendSelection = true)
                    true
                }
                KeyEvent.VK_C -> selectedText()?.let(ClipboardService::writeText) ?: false
                KeyEvent.VK_X -> cut()
                KeyEvent.VK_V -> paste()
                else -> false
            }
        }
        val extend = event.modifiers and AwtInputEvent.SHIFT_DOWN_MASK != 0
        when (event.keyCode) {
            KeyEvent.VK_ENTER -> onSubmit()
            KeyEvent.VK_LEFT -> area.moveCaretTo(area.caret - 1, extend)
            KeyEvent.VK_RIGHT -> area.moveCaretTo(area.caret + 1, extend)
            KeyEvent.VK_HOME -> area.moveCaretTo(0, extend)
            KeyEvent.VK_END -> area.moveCaretTo(area.length, extend)
            KeyEvent.VK_DELETE -> deleteForward()
            else -> return false
        }
        return true
    }

    private fun selectedRange(): IntRange? = area.selectionRange()?.takeUnless(IntRange::isEmpty)

    private fun selectedText(): String? =
        selectedRange()?.let { area.textInRange(it.first, it.last + 1) }

    private fun cut(): Boolean {
        val range = selectedRange() ?: return false
        if (!ClipboardService.writeText(area.textInRange(range.first, range.last + 1))) return false
        area.delete(range.first, range.last + 1)
        return true
    }

    private fun paste(): Boolean {
        val clip = ClipboardService.readText() ?: return false
        area.typeAtCaret(sanitize(clip))
        return true
    }

    private fun deleteBackward() {
        val range = selectedRange()
        if (range != null) area.delete(range.first, range.last + 1)
        else if (area.caret > 0) area.delete(area.caret - 1, area.caret)
    }

    private fun deleteForward() {
        val range = selectedRange()
        if (range != null) area.delete(range.first, range.last + 1)
        else if (area.caret < area.length) area.delete(area.caret, area.caret + 1)
    }

    // --- mouse ------------------------------------------------------------------------------

    override fun onMouseEvent(event: InputEvent.MouseInput): Boolean = when (event) {
        is InputEvent.MousePressed ->
            if (enabled && event.button == MouseEvent.BUTTON1) {
                area.moveCaretTo(offsetAt(event.x))
                dragging = true
                true
            } else false
        is InputEvent.MouseDragged ->
            if (dragging) {
                area.moveCaretTo(offsetAt(event.x), extendSelection = true)
                true
            } else false
        is InputEvent.MouseReleased -> dragging.also { dragging = false }
        is InputEvent.MouseCancelled -> { dragging = false; true }
        else -> false
    }

    private fun offsetAt(rootX: Int): Int {
        val text = area.snapshot()
        val targetX = (rootX - bounds.x - PADDING + scrollX).coerceAtLeast(0)
        var previousWidth = 0
        for (column in text.indices) {
            val width = textWidth(text.take(column + 1))
            if (targetX < (previousWidth + width) / 2) return column
            previousWidth = width
        }
        return text.length
    }

    // --- input method -----------------------------------------------------------------------

    override fun updateComposition(committedText: String, composedText: String, caretInComposition: Int): Boolean {
        if (committedText.isNotEmpty()) {
            area.typeAtCaret(sanitize(committedText))
            compositionAnchor = null
            compositionReplacementEnd = null
        }
        if (composedText.isNotEmpty() && compositionAnchor == null) {
            val selection = selectedRange()
            compositionAnchor = selection?.first ?: area.caret
            compositionReplacementEnd = selection?.let { it.last + 1 } ?: area.caret
        }
        this.composedText = sanitize(composedText)
        compositionCaret = caretInComposition.coerceIn(0, this.composedText.length)
        if (this.composedText.isEmpty()) {
            compositionAnchor = null
            compositionReplacementEnd = null
        }
        requestRepaint()
        return true
    }

    override fun updateCompositionCaret(caretInComposition: Int) {
        compositionCaret = caretInComposition.coerceIn(0, composedText.length)
        requestRepaint()
    }

    override fun inputMethodTextLocation(offsetInComposition: Int): Rectangle {
        val display = displayText()
        val index = (compositionAnchor ?: area.caret) + offsetInComposition.coerceIn(0, composedText.length)
        val x = PADDING - scrollX + textWidth(display.take(index))
        return Rectangle(bounds.x + x, bounds.y + 4, 1, bounds.height - 8)
    }

    override fun inputMethodInsertOffset(): Int = compositionAnchor ?: area.caret

    override fun committedText(beginIndex: Int, endIndex: Int): String {
        require(beginIndex in 0..endIndex && endIndex <= area.length) { "invalid committed text range" }
        return area.textInRange(beginIndex, endIndex)
    }

    override fun committedTextLength(): Int = area.length

    override fun selectedTextForInputMethod(): String? = selectedText()

    private fun textWidth(text: String): Int =
        theme.bodyFont.getStringBounds(text, FRC).width.roundToInt()

    companion object {
        const val HEIGHT = 44
        private const val PADDING = 14
        private const val RADIUS = 8
        private val FRC = FontRenderContext(null, true, true)
        private val SHORTCUT_MASK = try {
            Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx
        } catch (_: Exception) {
            AwtInputEvent.CTRL_DOWN_MASK
        }

        private fun sanitize(text: String): String = text.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ')
    }
}
