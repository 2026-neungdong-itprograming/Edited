package io.github.nd2026.edited.ui

import io.github.nd2026.edited.event.InputEvent

/** Routes pointer input and keeps a pressed widget captured until release or cancellation. */
class PointerRouter(
    private val root: () -> Widget?,
    private val focusManager: FocusManager,
) {
    private var captured: Widget? = null
    private var hovered: Widget? = null

    fun pressed(event: InputEvent.MousePressed) {
        updateHovered(event.x, event.y, event.modifiers)
        val target = root()?.hitTest(event.x, event.y) ?: return
        if (target.focusable) focusManager.requestFocus(target)
        target.pressed = true
        if (target.onMouseEvent(event)) captured = target else target.pressed = false
    }

    fun released(event: InputEvent.MouseReleased) {
        val target = captured ?: root()?.hitTest(event.x, event.y)
        target?.onMouseEvent(event)
        captured?.pressed = false
        captured = null
        updateHovered(event.x, event.y, event.modifiers)
    }

    fun dragged(event: InputEvent.MouseDragged) {
        (captured ?: root()?.hitTest(event.x, event.y))?.onMouseEvent(event)
    }

    fun moved(event: InputEvent.MouseMoved) {
        updateHovered(event.x, event.y, event.modifiers)
        hovered?.onMouseEvent(event)
    }

    fun scrolled(event: InputEvent.Scroll) {
        root()?.hitTest(event.x, event.y)?.onMouseEvent(event)
    }

    fun exited(x: Int, y: Int, modifiers: Int) {
        hovered?.let {
            it.hovered = false
            it.onMouseEvent(InputEvent.MouseExited(x, y, modifiers))
        }
        hovered = null
    }

    fun cancel(x: Int = -1, y: Int = -1, modifiers: Int = 0) {
        captured?.let {
            it.onMouseEvent(InputEvent.MouseCancelled(x, y, modifiers))
            it.pressed = false
        }
        captured = null
    }

    private fun updateHovered(x: Int, y: Int, modifiers: Int) {
        val next = root()?.hitTest(x, y)
        if (next === hovered) return
        hovered?.let {
            it.hovered = false
            it.onMouseEvent(InputEvent.MouseExited(x, y, modifiers))
        }
        hovered = next
        next?.let {
            it.hovered = true
            it.onMouseEvent(InputEvent.MouseEntered(x, y, modifiers))
        }
    }
}
