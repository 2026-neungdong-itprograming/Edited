package io.github.nd2026.edited

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.test.Test
import kotlin.test.assertTrue

class ArchitectureBoundaryTest {
    @Test
    fun `only the platform bridge imports Swing`() {
        val sourceRoot = sequenceOf(Path.of("src/main/kotlin"), Path.of("app/src/main/kotlin"))
            .first(Files::isDirectory)
        val allowed = setOf(
            "io/github/nd2026/edited/App.kt",
            "io/github/nd2026/edited/ui/RootPane.kt",
            "io/github/nd2026/edited/render/RenderScheduler.kt",
        )
        val violations = Files.walk(sourceRoot).use { paths ->
            paths.filter { it.extension == "kt" }
                .flatMap { file ->
                    val relative = sourceRoot.relativize(file).invariantSeparatorsPathString
                    Files.readAllLines(file).stream()
                        .filter { it.trimStart().startsWith("import javax.swing.") }
                        .filter { relative !in allowed }
                        .map { "$relative: ${it.trim()}" }
                }
                .toList()
        }

        assertTrue(violations.isEmpty(), "Swing imports outside the platform bridge:\n${violations.joinToString("\n")}")
    }
}
