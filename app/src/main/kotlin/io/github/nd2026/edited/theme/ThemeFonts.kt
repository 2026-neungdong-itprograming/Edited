package io.github.nd2026.edited.theme

import java.awt.Font
import java.awt.GraphicsEnvironment
import javax.swing.text.StyleContext

/**
 * The UI font: Noto Sans KR bundled in `resources/fonts/` (SIL OFL, see `fonts/OFL.txt`), so it
 * looks the same on every PC whether or not the font is installed. Falls back to Java's logical
 * sans-serif if the file can't be loaded.
 */
object ThemeFonts {
    private const val RESOURCE = "/fonts/NotoSansKR-VF.ttf"

    /** The bundled font itself, or null if it couldn't be loaded. */
    private val bundled: Font? by lazy {
        runCatching {
            ThemeFonts::class.java.getResourceAsStream(RESOURCE)!!.use { Font.createFont(Font.TRUETYPE_FONT, it) }
        }.onFailure { System.err.println("Bundled font $RESOURCE not loaded: ${it.message}") }.getOrNull()
    }

    /** Family name to request: the bundled font once registered with the JVM, else sans-serif. */
    val family: String by lazy {
        val font = bundled ?: return@lazy Font.SANS_SERIF
        // registerFont returns false when a font with that name is already installed; either way it's available by name.
        runCatching { GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font) }
        font.family
    }

    /**
     * A composite font: glyphs Noto Sans KR lacks (▾ ⌕ ⚙ …) fall back to the system fonts instead
     * of rendering as boxes, which a plain `Font(family, …)` would not do.
     */
    fun of(size: Int, style: Int = Font.PLAIN): Font = StyleContext.getDefaultStyleContext().getFont(family, style, size)
}
