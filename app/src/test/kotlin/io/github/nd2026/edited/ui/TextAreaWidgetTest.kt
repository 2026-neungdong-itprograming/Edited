package io.github.nd2026.edited.ui

import io.github.nd2026.edited.core.TextArea
import io.github.nd2026.edited.event.InputEvent
import io.github.nd2026.edited.ui.components.ContextMenuItem
import io.github.nd2026.edited.ui.components.ContextMenuWidget
import java.awt.event.InputEvent as AwtInputEvent
import java.awt.event.KeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextAreaWidgetTest {
    @Test
    fun `backspace replaces an active selection`() {
        val model = TextArea("가나다라마바사")
        val widget = TextAreaWidget(model)
        model.moveCaretTo(1)
        model.moveCaretTo(4, extendSelection = true)

        assertTrue(widget.onKeyEvent(InputEvent.KeyTyped('\b', 0)))

        assertEquals("가마바사", model.snapshot())
        assertEquals(1, model.caret)
    }

    @Test
    fun `shift arrow extends selection`() {
        val model = TextArea("abcd")
        val widget = TextAreaWidget(model)
        model.moveCaretTo(1)

        widget.onKeyEvent(InputEvent.KeyPressed(KeyEvent.VK_RIGHT, AwtInputEvent.SHIFT_DOWN_MASK))

        assertEquals(1..1, model.selectionRange())
    }

    @Test
    fun `context menu invokes released row and dismisses`() {
        var actions = 0
        var dismissals = 0
        val menu = ContextMenuWidget(
            listOf(ContextMenuItem("복사") { actions++ }),
            onDismiss = { dismissals++ },
        )
        menu.setBounds(20, 30, 180, ContextMenuWidget.preferredHeight(1))

        menu.onMouseEvent(InputEvent.MousePressed(40, 45, 1, 0))
        menu.onMouseEvent(InputEvent.MouseReleased(40, 45, 1, 0))

        assertEquals(1, actions)
        assertEquals(1, dismissals)
    }

    @Test
    fun `IME composition stays transient until it is committed`() {
        val model = TextArea("가나다")
        val widget = TextAreaWidget(model)
        model.moveCaretTo(1)

        widget.updateComposition("", "ㅎ", 1)
        widget.updateComposition("", "하", 1)

        assertEquals("가나다", model.snapshot())
        assertEquals(1, widget.inputMethodInsertOffset())

        widget.updateComposition("한", "", 0)

        assertEquals("가한나다", model.snapshot())
        assertEquals(2, model.caret)
    }

    @Test
    fun `committed IME text replaces the original selection once`() {
        val model = TextArea("가나다라")
        val widget = TextAreaWidget(model)
        model.moveCaretTo(1)
        model.moveCaretTo(3, extendSelection = true)

        widget.updateComposition("", "하", 1)
        assertEquals("가나다라", model.snapshot())

        widget.updateComposition("한", "", 0)

        assertEquals("가한라", model.snapshot())
    }
}
