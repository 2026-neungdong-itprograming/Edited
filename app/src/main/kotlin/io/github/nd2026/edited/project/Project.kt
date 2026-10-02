package io.github.nd2026.edited.project

import java.nio.file.Files
import java.nio.file.Path

/**
 * An opened Texted project: a folder on disk identified by its [manifest].
 *
 * ```
 * <root>/
 *   texted.project.json   manifest
 *   manuscript/           chapters (.md)
 *   characters/           character sheets
 *   world/                world-building notes
 *   custom-rules.json     local user dictionary (see UserDictionaryLoader)
 * ```
 */
class Project private constructor(val root: Path, val manifest: ProjectManifest) {

    val title: String get() = manifest.title
    val manuscriptDir: Path get() = root.resolve(MANUSCRIPT_DIR)
    val charactersDir: Path get() = root.resolve(CHARACTERS_DIR)
    val worldDir: Path get() = root.resolve(WORLD_DIR)
    val userDictionary: Path get() = root.resolve(USER_DICTIONARY)

    /** Fresh snapshot of the folder tree (Git/editor-managed folders are not listed). */
    fun tree(): ProjectNode.Directory = ProjectNode.scan(root)

    /** All chapter files in reading order. */
    fun chapters(): List<ProjectNode.File> =
        ProjectNode.scan(manuscriptDir).descendants()
            .filterIsInstance<ProjectNode.File>()
            .filter { it.isChapter }
            .toList()

    companion object {
        const val MANIFEST_FILE = "texted.project.json"
        const val MANUSCRIPT_DIR = "manuscript"
        const val CHARACTERS_DIR = "characters"
        const val WORLD_DIR = "world"
        const val USER_DICTIONARY = "custom-rules.json"

        fun isProject(root: Path): Boolean = Files.isRegularFile(root.resolve(MANIFEST_FILE))

        fun open(root: Path): Project {
            val manifestFile = root.resolve(MANIFEST_FILE)
            require(Files.isRegularFile(manifestFile)) { "프로젝트가 아닙니다: $MANIFEST_FILE 이(가) 없습니다." }
            return Project(root, ProjectManifest.fromJson(Files.readString(manifestFile)))
        }

        internal fun of(root: Path, manifest: ProjectManifest) = Project(root, manifest)
    }
}
