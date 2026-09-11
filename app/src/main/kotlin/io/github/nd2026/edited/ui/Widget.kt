package io.github.nd2026.edited.ui

import io.github.nd2026.edited.event.InputEvent
import io.github.nd2026.edited.render.Painter
import io.github.nd2026.edited.theme.Theme
import io.github.nd2026.edited.theme.ThemeProvider
import java.awt.Graphics2D
import java.awt.Rectangle
import io.github.nd2026.edited.ui.layout.Constraints
import io.github.nd2026.edited.ui.layout.IntSize

/**
 * Base of the toolkit's own retained widget tree - the Kotlin analogue of Swing's `JComponent`,
 * but owned entirely by this toolkit rather than AWT. A single [RootPane] (a `JComponent`) is
 * the only AWT bridge per top-level window; everything below it - layout, painting, hit
 * testing, focus - is this class's responsibility, so concrete widgets never touch AWT
 * directly.
 *
 * [bounds] is always in root-relative (not parent-relative) coordinates, which keeps painting,
 * hit-testing and dirty-region math free of coordinate-translation bugs when walking the tree.
 */
abstract class Widget : Painter {

    var bounds: Rectangle = Rectangle()
        internal set

    var parent: Widget? = null
        internal set

    /** The [RootPane] hosting this widget, or null if not attached to one. */
    var hostPane: RootPane? = null
        private set

    val children: MutableList<Widget> = mutableListOf()

    var visible: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            requestRepaint()
        }

    var enabled: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            if (!value) {
                hovered = false
                pressed = false
            }
            requestRepaint()
        }

    var focusable: Boolean = false
    var selected: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            requestRepaint()
        }

    var hovered: Boolean = false
        internal set(value) {
            if (field == value) return
            field = value
            requestRepaint()
        }

    var pressed: Boolean = false
        internal set(value) {
            if (field == value) return
            field = value
            requestRepaint()
        }

    var focused: Boolean = false
        internal set(value) {
            if (field == value) return
            field = value
            requestRepaint()
        }

    var semantics: Semantics = Semantics()

    var measuredSize: IntSize = IntSize.Zero
        private set

    val theme: Theme
        get() = hostPane?.themeProvider?.current ?: ThemeProvider.default.current

    fun addChild(child: Widget) {
        require(child.parent == null) { "widget already has a parent" }
        children.add(child)
        child.parent = this
        child.propagateHostPane(hostPane)
        child.onAttach()
    }

    fun removeChild(child: Widget) {
        if (children.remove(child)) {
            child.onDetach()
            child.parent = null
            child.propagateHostPane(null)
        }
    }

    internal fun propagateHostPane(pane: RootPane?) {
        hostPane = pane
        for (child in children) child.propagateHostPane(pane)
    }

    fun setBounds(x: Int, y: Int, width: Int, height: Int) {
        bounds = Rectangle(x, y, width, height)
        layout()
    }

    fun measure(constraints: Constraints = Constraints()): IntSize {
        measuredSize = constraints.constrain(onMeasure(constraints))
        return measuredSize
    }

    protected open fun onMeasure(constraints: Constraints): IntSize =
        constraints.constrain(IntSize(bounds.width, bounds.height))

    /** Requests a repaint of [region] (root-relative), or this widget's whole bounds if null. */
    fun requestRepaint(region: Rectangle? = null) {
        hostPane?.scheduler?.requestRepaint(region ?: bounds)
    }

    /** Positions [children] within [bounds]. No-op by default; [Container] delegates to a LayoutManager. */
    open fun layout() {
        for (child in children) child.layout()
    }

    open fun hitTest(x: Int, y: Int): Widget? {
        if (!visible || !enabled || !bounds.contains(x, y)) return null
        for (i in children.indices.reversed()) {
            children[i].hitTest(x, y)?.let { return it }
        }
        return this
    }

    final override fun paint(g: Graphics2D, region: Rectangle) {
        if (!visible || !bounds.intersects(region)) return
        val translated = g.create(bounds.x, bounds.y, bounds.width, bounds.height) as Graphics2D
        try {
            onPaint(translated, Rectangle(0, 0, bounds.width, bounds.height))
        } finally {
            translated.dispose()
        }
        for (child in children) child.paint(g, region)
    }

    /** Override to draw this widget's own content, in widget-local coordinates. */
    protected open fun onPaint(g: Graphics2D, localRegion: Rectangle) {}

    open fun onAttach() {}
    open fun onDetach() {}

    /** Return true if the event was consumed and should not propagate further. */
    open fun onKeyEvent(event: InputEvent.KeyInput): Boolean = false
    open fun onMouseEvent(event: InputEvent.MouseInput): Boolean = false
}
