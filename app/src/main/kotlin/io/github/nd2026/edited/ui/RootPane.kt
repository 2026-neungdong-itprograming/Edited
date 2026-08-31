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
                dispatchKey(InputEvent.KeyPressed(e.keyCode, e.modifiersEx))
            }
        })

        val mouseHandler = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                requestFocusInWindow()
                val hit = content?.hitTest(e.x, e.y)
                if (hit != null && hit.focusable) focusManager.requestFocus(hit)
                hit?.onMouseEvent(InputEvent.MousePressed(e.x, e.y, e.button, e.modifiersEx))
            }

            override fun mouseReleased(e: MouseEvent) {
                content?.hitTest(e.x, e.y)?.onMouseEvent(InputEvent.MouseReleased(e.x, e.y, e.button, e.modifiersEx))
            }

            override fun mouseDragged(e: MouseEvent) {
                content?.hitTest(e.x, e.y)?.onMouseEvent(InputEvent.MouseDragged(e.x, e.y, e.modifiersEx))
            }

            override fun mouseMoved(e: MouseEvent) {
                content?.hitTest(e.x, e.y)?.onMouseEvent(InputEvent.MouseMoved(e.x, e.y, e.modifiersEx))
            }

            override fun mouseWheelMoved(e: MouseWheelEvent) {
                content?.hitTest(e.x, e.y)?.onMouseEvent(InputEvent.Scroll(e.x, e.y, e.preciseWheelRotation, e.modifiersEx))
            }
        }
        addMouseListener(mouseHandler)
        addMouseMotionListener(mouseHandler)
        addMouseWheelListener(mouseHandler)
    }

    private fun dispatchKey(event: InputEvent.KeyInput) {
        focusManager.focused?.onKeyEvent(event)
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
