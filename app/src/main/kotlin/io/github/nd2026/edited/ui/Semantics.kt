package io.github.nd2026.edited.ui

/** Platform-independent accessibility description for a toolkit-owned [Widget]. */
data class Semantics(
    val role: Role = Role.NONE,
    val name: String? = null,
    val description: String? = null,
    val value: String? = null,
    val actions: Set<Action> = emptySet(),
) {
    enum class Role { NONE, BUTTON, CHECK_BOX, DIALOG, MENU, MENU_ITEM, TAB, TAB_LIST, TEXT, TEXT_FIELD }
    enum class Action { ACTIVATE, DISMISS, FOCUS, SELECT, SET_VALUE }
}
