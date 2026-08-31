package io.github.nd2026.edited.render

import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage

/**
 * Off-screen double buffer for a widget subtree, backed by a [BufferedImage]. Deliberately
 * on-heap: `BufferedImage`/`VolatileImage` are already managed and accelerated by Java2D, so
 * pushing pixel storage off-heap (unlike the text storage layer, see
 * [io.github.nd2026.edited.core.offheap.NativeTextBuffer]) would fight that acceleration path
 * for no demonstrated benefit. Revisit only if profiling shows framebuffer allocation/copy is
 * an actual bottleneck.
 */
class Surface(width: Int, height: Int) {

    var width: Int = width.coerceAtLeast(1)
        private set
    var height: Int = height.coerceAtLeast(1)
        private set

    private var image: BufferedImage = createImage(this.width, this.height)

    private fun createImage(w: Int, h: Int) = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)

    /** Reallocates the backing image if the requested size changed. */
    fun resize(newWidth: Int, newHeight: Int) {
        val w = newWidth.coerceAtLeast(1)
        val h = newHeight.coerceAtLeast(1)
        if (w == width && h == height) return
        width = w
        height = h
        image = createImage(w, h)
    }

    /** Graphics2D for painting into the buffer; caller must dispose() it. */
    fun graphics(): Graphics2D {
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        return g
    }

    /** Composites the buffered image onto the real screen graphics. */
    fun blit(g: Graphics) {
        g.drawImage(image, 0, 0, null)
    }
}
