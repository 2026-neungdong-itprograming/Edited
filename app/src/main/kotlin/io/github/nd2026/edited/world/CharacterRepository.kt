package io.github.nd2026.edited.world

import io.github.nd2026.edited.project.Project
import io.github.nd2026.edited.project.ProjectNode
import java.nio.file.Files
import java.nio.file.Path

data class CharacterSheet(val name: String, val aliases: List<String>, val description: String, val path: Path)

/**
 * Reads the character sheets in `characters/`. A sheet is a plain Markdown file:
 * ```
 * # 한서연
 * 별칭: 서연, 한 대리
 * 27세. 주인공의 직장 동료.
 * ```
 * The heading is the name (the file name if there is none), the `별칭:` line lists aliases, and
 * everything else is the description.
 */
object CharacterRepository {
    private val aliasLine = Regex("^별칭\\s*[:：]\\s*(.*)$")

    fun load(project: Project): List<CharacterSheet> = sheetFiles(project.charactersDir).map(::read)

    fun read(path: Path): CharacterSheet = parse(path, Files.readString(path))

    fun parse(path: Path, text: String): CharacterSheet {
        var name: String? = null
        val aliases = ArrayList<String>()
        val description = ArrayList<String>()
        for (raw in text.lines()) {
            val line = raw.trim()
            val alias = aliasLine.find(line)
            when {
                name == null && line.startsWith("#") && line.trimStart('#').isNotBlank() -> name = line.trimStart('#').trim()
                alias != null -> aliases += alias.groupValues[1].split(',', '、', '，').map(String::trim).filter(String::isNotEmpty)
                else -> description += raw.trimEnd()
            }
        }
        return CharacterSheet(
            name = name ?: path.fileName.toString().substringBeforeLast('.'),
            aliases = aliases,
            description = description.joinToString("\n").trim(),
            path = path,
        )
    }

    /** Markdown/text files under [dir] in natural order; empty if the folder does not exist. */
    internal fun sheetFiles(dir: Path): List<Path> {
        if (!Files.isDirectory(dir)) return emptyList()
        return ProjectNode.scan(dir).descendants()
            .filterIsInstance<ProjectNode.File>()
            .filter { it.isChapter }
            .map { it.path }
            .toList()
    }
}
