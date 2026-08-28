package io.github.jwyoon1220.app

import java.awt.Dimension
import javax.swing.JFrame
import javax.swing.SwingUtilities

fun main() {
    SwingUtilities.invokeLater {
        Main()
    }
}

class Main: JFrame() {
    init {
        this.title = "Texted"
        this.size = Dimension(800, 600)
        this.defaultCloseOperation = EXIT_ON_CLOSE
        this.isResizable = true
    }
}