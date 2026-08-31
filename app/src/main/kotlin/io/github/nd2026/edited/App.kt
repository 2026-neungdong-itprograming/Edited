package io.github.nd2026.edited

import io.github.nd2026.edited.ui.RootPane
import io.github.nd2026.edited.ui.TextAreaWidget
import java.awt.Dimension
import javax.swing.JFrame
import javax.swing.SwingUtilities

fun main() {
    SwingUtilities.invokeLater {
        Main().isVisible = true
    }
}

class Main : JFrame() {
    init {
        this.title = "Texted"
        this.size = Dimension(800, 600)
        this.defaultCloseOperation = EXIT_ON_CLOSE
        this.isResizable = true
        val rootPane = RootPane()
        rootPane.content = TextAreaWidget()
        this.contentPane.add(rootPane)
    }
}
