package io.github.nd2026.edited.ui

import io.github.nd2026.edited.event.InputEvent

fun interface Command {
    fun execute(): Boolean
}

data class KeyStroke(val keyCode: Int, val modifiers: Int)

/** Toolkit-owned replacement for Swing InputMap/ActionMap. */
class Keymap(private val parent: Keymap? = null) {
    private val bindings = mutableMapOf<KeyStroke, Command>()

    fun bind(stroke: KeyStroke, command: Command) {
        bindings[stroke] = command
    }

    fun unbind(stroke: KeyStroke) {
        bindings.remove(stroke)
    }

    fun dispatch(event: InputEvent.KeyPressed): Boolean =
        bindings[KeyStroke(event.keyCode, event.modifiers)]?.execute() == true ||
            parent?.dispatch(event) == true
}
