package io.github.nd2026.edited.project

import java.nio.file.Files
import java.nio.file.Path

/**
 * File operations behind the project tree's context menu: create episodes, sheets and folders,
 * rename, and change a file's extension. Every operation stays inside the project folder and
 * refuses to overwrite; failures are [IllegalArgumentException]s with a message for the author.
 */
object ProjectFiles {
    private val ORDER_PREFIX = Regex("^(\\d+)[.)]\\s*")

    /** First problem with [name] as a file or folder name (without the folder part), or null. */
    fun validateName(name: String): String? {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> "이름을 입력하세요."
            trimmed.any { it in ProjectService.ILLEGAL_NAME_CHARS || it.isISOControl() } -> "이름에 쓸 수 없는 문자가 있습니다: < > : \" / \\ | ? *"
            trimmed.endsWith('.') -> "이름은 마침표로 끝날 수 없습니다."
            trimmed.uppercase().substringBefore('.') in ProjectService.RESERVED_NAMES -> "사용할 수 없는 이름입니다: $trimmed"
            trimmed.length > 120 -> "이름이 너무 깁니다. (120자 이하)"
            else -> null
        }
    }

    /**
     * Where a new [format] document goes when the author right-clicked [target]: [target] itself
     * (or its folder) if that is inside the format's home folder, else the home folder - so
     * 새로 만들기 › 캐릭터 on a chapter still lands in `characters/`.
     */
    fun folderFor(project: Project, format: NovelFormat, target: Path): Path {
        val home = project.root.resolve(format.folder).toAbsolutePath().normalize()
        val dir = (if (Files.isDirectory(target)) target else target.parent).toAbsolutePath().normalize()
        return if (dir.startsWith(home)) dir else home
    }

    /** The title suggested for the next episode in [dir]: "제N화" after the highest order number there. */
    fun nextEpisodeTitle(dir: Path): String = "제${nextOrder(dir)}화"

    /**
     * Creates `NN. [title].episode` (episodes, numbered after the last one in [dir]) or `[title].character`/
     * `.world` holding the format's template, and returns its path.
     */
    fun create(project: Project, format: NovelFormat, dir: Path, title: String): Path {
        validateName(title)?.let { throw IllegalArgumentException(it) }
        Files.createDirectories(dir)
        val name = if (format == NovelFormat.EPISODE) {
            val order = nextOrder(dir)
            "%0${maxOf(2, order.toString().length)}d. %s".format(order, title.trim()) + format.dotExtension
        } else {
            title.trim() + format.dotExtension
        }
        val path = inside(project, dir.resolve(name))
        require(!Files.exists(path)) { "이미 같은 이름이 있습니다: $name" }
        Files.writeString(path, format.template)
        return path
    }

    fun createFolder(project: Project, dir: Path, name: String): Path {
        validateName(name)?.let { throw IllegalArgumentException(it) }
        val path = inside(project, dir.resolve(name.trim()))
        require(!Files.exists(path)) { "이미 같은 이름이 있습니다: ${name.trim()}" }
        return Files.createDirectories(path)
    }

    /** Renames [path] (file or folder) to [newName] in the same folder and returns the new path. */
    fun rename(project: Project, path: Path, newName: String): Path {
        validateName(newName)?.let { throw IllegalArgumentException(it) }
        val source = inside(project, path)
        require(source != project.root.toAbsolutePath().normalize()) { "프로젝트 폴더의 이름은 바꿀 수 없습니다." }
        val target = inside(project, source.resolveSibling(newName.trim()))
        if (target == source) return source
        // A case-only rename ("a.md" → "A.md") is the same file on Windows, not a clash.
        require(!Files.exists(target) || Files.isSameFile(source, target)) { "이미 같은 이름이 있습니다: ${newName.trim()}" }
        return Files.move(source, target)
    }

    /** Gives the file at [path] the [extension] (without the dot), keeping its name and content. */
    fun changeExtension(project: Project, path: Path, extension: String): Path {
        val name = path.fileName.toString()
        val base = if ('.' in name) name.substringBeforeLast('.') else name
        return rename(project, path, "$base.${extension.trimStart('.')}")
    }

    /** The file name without extension, for the rename box. */
    fun baseName(path: Path): String = path.fileName.toString().let { if ('.' in it && !Files.isDirectory(path)) it.substringBeforeLast('.') else it }

    private fun nextOrder(dir: Path): Int {
        if (!Files.isDirectory(dir)) return 1
        val highest = Files.list(dir).use { files ->
            files.toList().mapNotNull { ORDER_PREFIX.find(it.fileName.toString())?.groupValues?.get(1)?.toIntOrNull() }.maxOrNull()
        }
        return (highest ?: 0) + 1
    }

    private fun inside(project: Project, path: Path): Path {
        val normalized = path.toAbsolutePath().normalize()
        require(normalized.startsWith(project.root.toAbsolutePath().normalize())) { "프로젝트 밖의 경로입니다." }
        return normalized
    }
}
