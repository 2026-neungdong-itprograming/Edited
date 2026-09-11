package io.github.nd2026.edited.ui

import io.github.nd2026.edited.event.InputEvent
import io.github.nd2026.edited.render.RenderScheduler
import io.github.nd2026.edited.render.Surface
import io.github.nd2026.edited.theme.ThemeProvider
import java.awt.Graphics
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.InputMethodEvent
import java.awt.event.InputMethodListener
import java.awt.font.TextHitInfo
import java.awt.im.InputMethodRequests
import java.text.AttributedCharacterIterator
import java.text.AttributedString
import javax.swing.JComponent

/**
 * The single AWT bridge per top-level window: one real `JComponent` that owns everything
 * below it - a [Widget] tree, a [Surface] double buffer, a [RenderScheduler], a [ThemeProvider]
 * and a [FocusManager]. AWT is used only for native windowing/DPI/input; layout, painting,
 * hit-testing and focus inside this component are entirely owned by the toolkit's own [Widget]
 * tree, not delegated back to Swing.
 */
class RootPane : JComponent() {

    val scheduler = RenderScheduler(this)
    val themeProvider = ThemeProvider()
    val focusManager = FocusManager()
    val keymap = Keymap()
    private val pointerRouter = PointerRouter({ content }, focusManager)

    private val surface = Surface(1, 1)

    var content: Widget? = null
        set(value) {
            field?.propagateHostPane(null)
            field = value
            value?.propagateHostPane(this)
            value?.setBounds(0, 0, width.coerceAtLeast(1), height.coerceAtLeast(1))
        }

    init {
        isFocusable = true
        enableInputMethods(true)
        background = themeProvider.current.background

        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                surface.resize(width.coerceAtLeast(1), height.coerceAtLeast(1))
                content?.setBounds(0, 0, width, height)
                scheduler.requestRepaint(Rectangle(0, 0, width, height))
            }
        })

        addKeyListener(object : KeyAdapter() {
            override fun keyTyped(e: KeyEvent) {
                dispatchKey(InputEvent.KeyTyped(e.keyChar, e.modifiersEx))
            }

            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_TAB) {
                    val backwards = e.modifiersEx and KeyEvent.SHIFT_DOWN_MASK != 0
                    if (content?.let { focusManager.moveFocus(it, backwards) } == true) e.consume()
                } else {
                    dispatchKey(InputEvent.KeyPressed(e.keyCode, e.modifiersEx))
                }
            }

            override fun keyReleased(e: KeyEvent) {
                dispatchKey(InputEvent.KeyReleased(e.keyCode, e.modifiersEx))
            }
        })

        addInputMethodListener(object : InputMethodListener {
            override fun inputMethodTextChanged(event: InputMethodEvent) {
                val client = focusManager.focused as? TextInputClient ?: return
                val text = attributedTextToString(event.text)
                val committedCount = event.committedCharacterCount.coerceIn(0, text.length)
                val committed = text.take(committedCount)
                val composed = text.drop(committedCount)
                val caret = event.caret?.insertionIndex ?: composed.length
                if (client.updateComposition(committed, composed, caret)) event.consume()
            }

            override fun caretPositionChanged(event: InputMethodEvent) {
                val client = focusManager.focused as? TextInputClient ?: return
                client.updateCompositionCaret(event.caret?.insertionIndex ?: 0)
                event.consume()
            }
        })

        val mouseHandler = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                requestFocusInWindow()
                if (focusManager.focused is TextInputClient) inputContext?.endComposition()
                pointerRouter.pressed(InputEvent.MousePressed(e.x, e.y, e.button, e.modifiersEx))
            }

            override fun mouseReleased(e: MouseEvent) {
                pointerRouter.released(InputEvent.MouseReleased(e.x, e.y, e.button, e.modifiersEx))
            }

            override fun mouseDragged(e: MouseEvent) {
                pointerRouter.dragged(InputEvent.MouseDragged(e.x, e.y, e.modifiersEx))
            }

            override fun mouseMoved(e: MouseEvent) {
                pointerRouter.moved(InputEvent.MouseMoved(e.x, e.y, e.modifiersEx))
            }

            override fun mouseWheelMoved(e: MouseWheelEvent) {
                pointerRouter.scrolled(InputEvent.Scroll(e.x, e.y, e.preciseWheelRotation, e.modifiersEx))
            }

            override fun mouseExited(e: MouseEvent) {
                pointerRouter.exited(e.x, e.y, e.modifiersEx)
            }
        }
        addMouseListener(mouseHandler)
        addMouseMotionListener(mouseHandler)
        addMouseWheelListener(mouseHandler)
        addFocusListener(object : FocusAdapter() {
            override fun focusLost(e: FocusEvent) {
                inputContext?.endComposition()
                pointerRouter.cancel()
            }
        })
    }

    override fun getInputMethodRequests(): InputMethodRequests = inputMethodBridge

    private val inputMethodBridge = object : InputMethodRequests {
        override fun getTextLocation(offset: TextHitInfo?): Rectangle {
            val client = focusManager.focused as? TextInputClient ?: return Rectangle()
            val local = client.inputMethodTextLocation(offset?.insertionIndex ?: 0)
            return try {
                val screen = locationOnScreen
                Rectangle(screen.x + local.x, screen.y + local.y, local.width, local.height)
            } catch (_: IllegalStateException) {
                local
            }
        }

        override fun getLocationOffset(x: Int, y: Int): TextHitInfo? = null

        override fun getInsertPositionOffset(): Int =
            (focusManager.focused as? TextInputClient)?.inputMethodInsertOffset() ?: 0

        override fun getCommittedText(
            beginIndex: Int,
            endIndex: Int,
            attributes: Array<out AttributedCharacterIterator.Attribute>?,
        ): AttributedCharacterIterator = iteratorOf(
            (focusManager.focused as? TextInputClient)?.committedText(beginIndex, endIndex).orEmpty(),
        )

        override fun getCommittedTextLength(): Int =
            (focusManager.focused as? TextInputClient)?.committedTextLength() ?: 0

        override fun cancelLatestCommittedText(
            attributes: Array<out AttributedCharacterIterator.Attribute>?,
        ): AttributedCharacterIterator? = null

        override fun getSelectedText(
            attributes: Array<out AttributedCharacterIterator.Attribute>?,
        ): AttributedCharacterIterator? =
            (focusManager.focused as? TextInputClient)?.selectedTextForInputMethod()?.let(::iteratorOf)
    }

    private fun dispatchKey(event: InputEvent.KeyInput) {
        val consumed = focusManager.focused?.onKeyEvent(event) == true
        if (!consumed && event is InputEvent.KeyPressed) keymap.dispatch(event)
    }

    override fun paintComponent(g: Graphics) {
        val region = g.clipBounds ?: Rectangle(0, 0, width, height)
        surface.resize(width.coerceAtLeast(1), height.coerceAtLeast(1))
        val sg = surface.graphics()
        try {
            sg.clip = region
            content?.paint(sg, region)
        } finally {
            sg.dispose()
        }
        surface.blit(g)
    }
}

internal fun attributedTextToString(iterator: AttributedCharacterIterator?): String {
    if (iterator == null) return ""
    return buildString {
        var current = iterator.first()
        while (current != AttributedCharacterIterator.DONE) {
            append(current)
            current = iterator.next()
        }
    }
}

private fun iteratorOf(text: String): AttributedCharacterIterator = AttributedString(text).iterator
