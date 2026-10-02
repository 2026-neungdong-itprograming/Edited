package io.github.nd2026.edited.project

import java.nio.file.Files
import java.nio.file.Path
import java.util.prefs.Preferences

/** Small persistent MRU list. Only normalized project paths are stored. */
object RecentProjects {
    private const val KEY = "recentProjectPaths"
    private const val SEPARATOR = "\n"
    private const val LIMIT = 8
    private val preferences = Preferences.userNodeForPackage(RecentProjects::class.java)

    fun list(): List<Path> {
        val valid = read().filter { Files.isDirectory(it) && Project.isProject(it) }.distinct()
        if (valid != read()) write(valid)
        return valid
    }

    fun record(project: Project) {
        val path = project.root.toAbsolutePath().normalize()
        write((listOf(path) + read().filterNot { it == path }).take(LIMIT))
    }

    fun clear() = preferences.remove(KEY)

    private fun read(): List<Path> = preferences.get(KEY, "")
        .split(SEPARATOR)
        .filter(String::isNotBlank)
        .mapNotNull { runCatching { Path.of(it) }.getOrNull() }

    private fun write(paths: List<Path>) {
        preferences.put(KEY, paths.joinToString(SEPARATOR) { it.toString() })
    }
}
