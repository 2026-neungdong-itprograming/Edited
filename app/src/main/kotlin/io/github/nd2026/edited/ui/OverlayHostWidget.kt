package io.github.nd2026.edited.ui

/** Owns normal content and toolkit-rendered popups/dialogs without Swing popup components. */
class OverlayHostWidget(content: Widget? = null) : Container() {
    private data class Entry(val widget: Widget, val modal: Boolean, val dismissOnOutside: Boolean)

    private val overlays = mutableListOf<Entry>()

    var content: Widget? = null
        set(value) {
            field?.let(::removeChild)
            field = value
            value?.let(::addChild)
        }

    init {
        this.content = content
    }

    override fun layout() {
        val b = bounds
        content?.setBounds(b.x, b.y, b.width, b.height)
        overlays.forEach { it.widget.layout() }
    }

    fun showOverlay(widget: Widget, modal: Boolean = false, dismissOnOutside: Boolean = false) {
        overlays += Entry(widget, modal, dismissOnOutside)
        addChild(widget)
        if (modal) hostPane?.focusManager?.setScope(widget)
        requestRepaint()
    }

    fun removeOverlay(widget: Widget): Boolean {
        val removed = overlays.removeAll { it.widget === widget }
        if (!removed) return false
        removeChild(widget)
        hostPane?.focusManager?.setScope(overlays.lastOrNull { it.modal }?.widget)
        requestRepaint()
        return true
    }

    override fun hitTest(x: Int, y: Int): Widget? {
        if (!visible || !enabled || !bounds.contains(x, y)) return null
        for (entry in overlays.asReversed().toList()) {
            entry.widget.hitTest(x, y)?.let { return it }
            if (entry.modal) return this
            if (entry.dismissOnOutside) {
                removeOverlay(entry.widget)
                return this
            }
        }
        return content?.hitTest(x, y) ?: this
    }
}
