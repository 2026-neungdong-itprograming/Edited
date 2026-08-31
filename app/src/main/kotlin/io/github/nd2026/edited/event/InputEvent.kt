package io.github.nd2026.edited.event

/** Toolkit-level input events, translated from AWT events by `ui.RootPane`. */
sealed interface InputEvent {
    sealed interface KeyInput : InputEvent {
        val modifiers: Int
    }

    /** A single typed character, after IME/dead-key composition (mirrors AWT's KEY_TYPED). */
    data class KeyTyped(val char: Char, override val modifiers: Int) : KeyInput

    /** A non-printable key transition, e.g. arrows, backspace, tab (mirrors AWT's KEY_PRESSED). */
    data class KeyPressed(val keyCode: Int, override val modifiers: Int) : KeyInput

    sealed interface MouseInput : InputEvent {
        val x: Int
        val y: Int
        val modifiers: Int
    }

    data class MousePressed(override val x: Int, override val y: Int, val button: Int, override val modifiers: Int) : MouseInput
    data class MouseReleased(override val x: Int, override val y: Int, val button: Int, override val modifiers: Int) : MouseInput
    data class MouseDragged(override val x: Int, override val y: Int, override val modifiers: Int) : MouseInput
    data class MouseMoved(override val x: Int, override val y: Int, override val modifiers: Int) : MouseInput

    data class Scroll(override val x: Int, override val y: Int, val unitsToScroll: Double, override val modifiers: Int) : MouseInput
}
