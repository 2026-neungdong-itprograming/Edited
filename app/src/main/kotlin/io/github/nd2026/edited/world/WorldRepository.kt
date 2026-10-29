package io.github.nd2026.edited.world

import io.github.nd2026.edited.project.Project
import java.nio.file.Path

/** A world-building card (place, organisation, item...) from `world/`. */
data class WorldEntry(val name: String, val aliases: List<String>, val description: String, val path: Path)

/** Reads the `.md` files in `world/` with the same format as [CharacterRepository]. */
object WorldRepository {
    fun load(project: Project): List<WorldEntry> = CharacterRepository.sheetFiles(project.worldDir).map { path ->
        CharacterRepository.read(path).let { WorldEntry(it.name, it.aliases, it.description, it.path) }
    }
}
