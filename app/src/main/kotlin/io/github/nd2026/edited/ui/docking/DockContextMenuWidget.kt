package io.github.nd2026.edited.ui.docking

import io.github.nd2026.edited.ui.OverlayHostWidget
import io.github.nd2026.edited.ui.components.ContextMenuItem
import io.github.nd2026.edited.ui.components.ContextMenuWidget

/**
 * Dock commands for one pane: move to a neighbouring group, split off, float/dock, hide, close
 * and reset the layout. Every entry calls the same [DockHostWidget] method as its shortcut.
 */
class DockContextMenuWidget private constructor(
    val paneId: PaneId,
    items: List<ContextMenuItem>,
    onDismiss: () -> Unit,
) : ContextMenuWidget(items, onDismiss) {
    val labels: List<String> get() = items.map { it.label }

    /** Runs the enabled item labelled [label]; returns false when it is missing or disabled. */
    fun activate(label: String): Boolean {
        val item = items.firstOrNull { it.label == label && it.enabled } ?: return false
        item.action()
        return true
    }

    companion object {
        private const val WIDTH = 260

        fun itemsFor(host: DockHostWidget, paneId: PaneId): List<ContextMenuItem> {
            val floating = host.isFloating(paneId)
            return listOf(
                ContextMenuItem("왼쪽 그룹으로 이동", "Alt+Shift+←", host.canMove(paneId, DockEdge.LEFT)) { host.movePane(paneId, DockEdge.LEFT) },
                ContextMenuItem("오른쪽 그룹으로 이동", "Alt+Shift+→", host.canMove(paneId, DockEdge.RIGHT)) { host.movePane(paneId, DockEdge.RIGHT) },
                ContextMenuItem("위 그룹으로 이동", "Alt+Shift+↑", host.canMove(paneId, DockEdge.TOP)) { host.movePane(paneId, DockEdge.TOP) },
                ContextMenuItem("아래 그룹으로 이동", "Alt+Shift+↓", host.canMove(paneId, DockEdge.BOTTOM)) { host.movePane(paneId, DockEdge.BOTTOM) },
                ContextMenuItem("오른쪽으로 분할", "Ctrl+Alt+Shift+→", host.canSplit(paneId)) { host.splitPane(paneId, DockDrop.RIGHT) },
                ContextMenuItem("아래로 분할", "Ctrl+Alt+Shift+↓", host.canSplit(paneId)) { host.splitPane(paneId, DockDrop.BOTTOM) },
                if (floating) ContextMenuItem("도킹", "Alt+Shift+F") { host.dockPane(paneId) }
                else ContextMenuItem("플로팅", "Alt+Shift+F") { host.floatPane(paneId) },
                ContextMenuItem("숨기기", "Shift+Esc", host.canHide(paneId)) { host.hidePane(paneId) },
                ContextMenuItem("닫기", "", host.canClose(paneId)) { host.closePane(paneId) },
                ContextMenuItem("레이아웃 초기화", "Alt+Shift+R") { host.resetLayout() },
            )
        }

        /** Shows the menu for [paneId] at root-relative ([x], [y]), kept inside [overlay], and focuses it. */
        fun show(host: DockHostWidget, paneId: PaneId, overlay: OverlayHostWidget, x: Int, y: Int): DockContextMenuWidget {
            lateinit var menu: DockContextMenuWidget
            val items = itemsFor(host, paneId)
            menu = DockContextMenuWidget(paneId, items) { overlay.removeOverlay(menu) }
            val height = preferredHeight(items.size)
            val ob = overlay.bounds
            val left = x.coerceIn(ob.x + 4, (ob.x + ob.width - WIDTH - 4).coerceAtLeast(ob.x + 4))
            val top = y.coerceIn(ob.y + 4, (ob.y + ob.height - height - 4).coerceAtLeast(ob.y + 4))
            menu.setBounds(left, top, WIDTH, height)
            overlay.showOverlay(menu, dismissOnOutside = true)
            menu.hostPane?.focusManager?.requestFocus(menu)
            menu.moveHighlight(1)
            return menu
        }
    }
}
