package io.github.nd2026.edited.ui.docking

import io.github.nd2026.edited.theme.Theme
import io.github.nd2026.edited.ui.OverlayHostWidget
import io.github.nd2026.edited.ui.RootPane
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JFrame

/** A floating pane's OS window, as seen by [DockHostWidget]. */
interface FloatingPaneWindow {
    val paneId: PaneId
    val bounds: Rectangle
    fun moveTo(bounds: Rectangle)
    fun applyTheme(theme: Theme)

    /** Repaints after the layout's focus changed. */
    fun refresh()
    fun toFront()
    fun showContextMenu(): Boolean

    /** Releases the pane content and disposes the window. */
    fun close()
}

fun interface FloatingWindowFactory {
    fun open(host: DockHostWidget, descriptor: PaneDescriptor, bounds: Rectangle): FloatingPaneWindow
}

/**
 * Floating pane window: a new `JFrame` holding its own [RootPane], whose content is a
 * [DockPaneWidget] for the single floating pane (inside an [OverlayHostWidget] for its menu).
 * Closing the window docks the pane back; moving or resizing it updates the saved bounds.
 */
class FloatingDockWindow(
    private val host: DockHostWidget,
    descriptor: PaneDescriptor,
    initialBounds: Rectangle,
) : FloatingPaneWindow {
    override val paneId: PaneId = descriptor.id
    private val frame = JFrame(descriptor.title)
    private val root = RootPane()
    private val pane = DockPaneWidget(host, DockNode.Tabs(listOf(paneId)), floating = true)
    private val overlay = OverlayHostWidget(pane)
    private var closing = false

    init {
        frame.type = java.awt.Window.Type.UTILITY
        frame.defaultCloseOperation = JFrame.DO_NOTHING_ON_CLOSE
        frame.contentPane.add(root)
        root.content = overlay
        host.bindKeys(root.keymap) { paneId }
        frame.bounds = Rectangle(initialBounds)
        frame.addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent) {
                if (!closing) host.dockPane(paneId)
            }

            override fun windowActivated(e: WindowEvent) {
                if (!closing && host.state.focusedPane != paneId) host.focusPane(paneId)
            }
        })
        frame.addComponentListener(object : ComponentAdapter() {
            override fun componentMoved(e: ComponentEvent) = report()
            override fun componentResized(e: ComponentEvent) = report()
        })
        frame.isVisible = true
    }

    private fun report() {
        if (!closing) host.onFloatingWindowMoved(paneId, frame.bounds)
    }

    override val bounds: Rectangle get() = frame.bounds

    override fun moveTo(bounds: Rectangle) {
        frame.bounds = bounds
    }

    override fun applyTheme(theme: Theme) {
        root.themeProvider.current = theme
        root.scheduler.requestRepaint(Rectangle(0, 0, root.width, root.height))
    }

    override fun refresh() {
        pane.requestRepaint()
    }

    override fun toFront() {
        frame.toFront()
    }

    override fun showContextMenu(): Boolean {
        host.showContextMenu(paneId, overlay, pane.bounds.x + 8, pane.bounds.y + DockPaneWidget.HEADER_HEIGHT)
        return true
    }

    override fun close() {
        closing = true
        pane.release()
        root.content = null
        frame.dispose()
    }
}
