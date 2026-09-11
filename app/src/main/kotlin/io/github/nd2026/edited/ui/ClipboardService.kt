package io.github.nd2026.edited.ui

import java.awt.HeadlessException
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

/** Small AWT bridge for the operating-system text clipboard. */
object ClipboardService {
    fun writeText(text: String): Boolean = try {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        true
    } catch (_: IllegalStateException) {
        false
    } catch (_: HeadlessException) {
        false
    }

    fun readText(): String? = try {
        Toolkit.getDefaultToolkit().systemClipboard
            .takeIf { it.isDataFlavorAvailable(DataFlavor.stringFlavor) }
            ?.getData(DataFlavor.stringFlavor) as? String
    } catch (_: Exception) {
        null
    }
}
