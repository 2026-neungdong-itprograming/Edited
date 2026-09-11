package io.github.nd2026.edited.ui

import io.github.nd2026.edited.event.InputEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PointerAndFocusTest {
    @Test
    fun `captured widget receives release outside its bounds`() {
        val focus = FocusManager()
        val root = Container()
        val target = RecordingWidget()
        root.addChild(target)
        root.setBounds(0, 0, 200, 100)
        target.setBounds(0, 0, 50, 50)
        val router = PointerRouter({ root }, focus)

        router.pressed(InputEvent.MousePressed(10, 10, 1, 0))
        router.dragged(InputEvent.MouseDragged(150, 80, 0))
        router.released(InputEvent.MouseReleased(150, 80, 1, 0))

        assertEquals(
            listOf("enter", "press", "drag", "release", "exit"),
            target.events,
        )
        assertFalse(target.pressed)
    }

    @Test
    fun `focus traversal wraps and respects direction`() {
        val root = Container()
        val first = RecordingWidget().apply { focusable = true }
        val second = RecordingWidget().apply { focusable = true }
        root.addChild(first)
        root.addChild(second)
        val focus = FocusManager()

        assertTrue(focus.moveFocus(root))
        assertSame(first, focus.focused)
        assertTrue(first.focused)

        focus.moveFocus(root)
        assertSame(second, focus.focused)
        focus.moveFocus(root, backwards = true)
        assertSame(first, focus.focused)
    }

    private class RecordingWidget : Widget() {
        val events = mutableListOf<String>()

        override fun onMouseEvent(event: InputEvent.MouseInput): Boolean {
            events += when (event) {
                is InputEvent.MouseEntered -> "enter"
                is InputEvent.MouseExited -> "exit"
                is InputEvent.MousePressed -> "press"
                is InputEvent.MouseDragged -> "drag"
                is InputEvent.MouseReleased -> "release"
                is InputEvent.MouseCancelled -> "cancel"
                else -> return false
            }
            return event !is InputEvent.MouseEntered && event !is InputEvent.MouseExited
        }
    }
}
