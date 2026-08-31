package io.github.nd2026.edited.core

/**
 * The visible line window of a [TextArea]. Any rendering backend (Swing, JavaFX, a game
 * canvas, a headless test harness) computes this from its own scroll position and asks the
 * core for exactly the text it needs, so full-document layout/painting is never required.
 */
data class Viewport(val firstVisibleLine: Int, val visibleLineCount: Int)
