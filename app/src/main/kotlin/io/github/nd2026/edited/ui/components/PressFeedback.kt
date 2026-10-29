package io.github.nd2026.edited.ui.components

import io.github.nd2026.edited.theme.Theme
import java.awt.Color
import javax.swing.Timer

/** Shared frame clock for UI transitions: one Swing timer that runs only while something animates. */
internal object FrameClock {
    private val active = LinkedHashSet<AnimatedValue>()
    private val timer = Timer(1000 / 120) { tick() }.apply { isCoalesce = true }

    fun start(value: AnimatedValue) {
        active += value
        if (!timer.isRunning) timer.start()
    }

    private fun tick() {
        val now = System.nanoTime()
        for (value in active.toList()) {
            value.frame()
            if (!value.isRunning(now)) active -= value
        }
        if (active.isEmpty()) timer.stop()
    }
}

/** A float that eases toward its target with Fluent's decelerate curve, calling [onFrame] every frame. */
class AnimatedValue(initial: Float = 0f, private val onFrame: () -> Unit) {
    private var from = initial
    private var to = initial
    private var start = 0L
    private var duration = 0L

    val target: Float get() = to

    val value: Float
        get() {
            if (duration == 0L) return to
            val t = ((System.nanoTime() - start).toFloat() / duration).coerceIn(0f, 1f)
            val eased = 1f - (1f - t) * (1f - t) * (1f - t)
            return from + (to - from) * eased
        }

    fun animateTo(target: Float, millis: Int) {
        if (target == to) return
        from = value
        to = target
        start = System.nanoTime()
        duration = millis * 1_000_000L
        FrameClock.start(this)
    }

    internal fun isRunning(now: Long) = now - start < duration
    internal fun frame() = onFrame()
}

/**
 * Windows 11 (WinUI) pointer feedback for one control, or one item of a list-like control:
 * a subtle fill fades in on hover, turns lighter while pressed and the content dims, with the
 * same 83 ms in / 167 ms out timing WinUI uses. Call [update] at the start of painting.
 */
class PressFeedback(repaint: () -> Unit) {
    private val hover = AnimatedValue(0f, repaint)
    private val press = AnimatedValue(0f, repaint)

    val hoverAmount: Float get() = hover.value
    val pressAmount: Float get() = press.value

    fun update(hovered: Boolean, pressed: Boolean) {
        hover.animateTo(if (hovered || pressed) 1f else 0f, if (hovered || pressed) FASTER else FAST)
        press.animateTo(if (pressed) 1f else 0f, if (pressed) FASTER else FAST)
    }

    /** The subtle fill over [theme]'s surface (SubtleFillColorSecondary → Tertiary), or null at rest. */
    fun subtleFill(theme: Theme): Color? {
        val dark = theme.isDark
        val hoverAlpha = if (dark) 15f else 10f
        val pressAlpha = if (dark) 10f else 6f
        val alpha = hoverAlpha * hoverAmount + (pressAlpha - hoverAlpha) * pressAmount
        return if (alpha < .5f) null else theme.onSurface.withAlpha(alpha.toInt().coerceAtLeast(1))
    }

    /** [color] dimmed toward TextFillColorSecondary while pressed. */
    fun content(color: Color): Color = color.withAlpha((color.alpha * (1f - PRESSED_DIM * pressAmount)).toInt())

    companion object {
        /** WinUI ControlFasterAnimationDuration / ControlFastAnimationDuration. */
        const val FASTER = 83
        const val FAST = 167

        /** WinUI ControlCornerRadius (4 px) as a Java2D arc diameter. */
        const val CORNER_ARC = 8

        private const val PRESSED_DIM = .21f
    }
}
