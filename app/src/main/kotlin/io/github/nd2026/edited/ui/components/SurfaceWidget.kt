package io.github.nd2026.edited.ui.components

import io.github.nd2026.edited.ui.Container
import io.github.nd2026.edited.ui.layout.Constraints
import io.github.nd2026.edited.ui.layout.IntSize
import io.github.nd2026.edited.ui.layout.StackLayout
import java.awt.Graphics2D
import java.awt.Rectangle

open class SurfaceWidget(
    var cornerRadius: Int = 0,
    var elevation: Int = 0,
) : Container(StackLayout) {
    override fun onMeasure(constraints: Constraints): IntSize {
        val childSize = children.firstOrNull()?.measure(constraints) ?: IntSize.Zero
        return constraints.constrain(childSize)
    }

    override fun onPaint(g: Graphics2D, localRegion: Rectangle) {
        val tint = theme.elevationTints[elevation.coerceIn(theme.elevationTints.indices)]
        g.color = composite(theme.surface, tint)
        if (cornerRadius > 0) {
            g.fillRoundRect(0, 0, bounds.width, bounds.height, cornerRadius * 2, cornerRadius * 2)
        } else {
            g.fillRect(0, 0, bounds.width, bounds.height)
        }
    }
}
