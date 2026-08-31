package io.github.nd2026.edited.render

import java.awt.Graphics2D
import java.awt.Rectangle

/** Implemented by anything that can paint itself into a clipped region. */
fun interface Painter {
    fun paint(g: Graphics2D, region: Rectangle)
}
