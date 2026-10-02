package io.github.nd2026.edited.project

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.PersonIdent
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant

/** What the New Project dialog collects. [location] is the parent folder; the project gets its own subfolder. */
data class NewProjectRequest(
    val title: String,
    val location: String,
    val author: String = "",
    val initGit: Boolean = true,
)

data class CreatedProject(val project: Project, val warnings: List<String>)

object ProjectService {

    // Characters Windows forbids in file names (a superset of what POSIX forbids).
    private val ILLEGAL_NAME_CHARS = "<>:\"/\\|?*".toSet()
    private val RESERVED_NAMES = setOf("CON", "PRN", "AUX", "NUL") +
        (1..9).flatMap { listOf("COM$it", "LPT$it") }

    const val FIRST_CHAPTER = "01. 제1화.md"

    fun defaultLocation(): String =
        Paths.get(System.getProperty("user.home"), "TextedProjects").toString()

    /** The folder name used for a project titled [title] (illegal characters become `_`). */
    fun folderNameFor(title: String): String =
        title.trim()
            .map { if (it in ILLEGAL_NAME_CHARS || it.isISOControl()) '_' else it }
            .joinToString("")
            .trim()
            .trimEnd('.')

    /** First problem with [request] as a user-facing message, or null if it can be created. */
    fun validate(request: NewProjectRequest): String? {
        val title = request.title.trim()
        if (title.isEmpty()) return "작품 제목을 입력하세요."
        val folder = folderNameFor(title)
        if (folder.isEmpty()) return "제목에 사용할 수 없는 문자만 있습니다."
        if (folder.uppercase().substringBefore('.') in RESERVED_NAMES) return "사용할 수 없는 제목입니다: $folder"
        if (folder.length > 100) return "제목이 너무 깁니다. (100자 이하)"
        if (request.location.isBlank()) return "저장 위치를 입력하세요."
        val parent = try {
            Paths.get(request.location.trim())
        } catch (_: InvalidPathException) {
            return "저장 위치가 올바르지 않습니다."
        }
        if (Files.exists(parent) && !Files.isDirectory(parent)) return "저장 위치가 폴더가 아닙니다."
        val target = parent.resolve(folder)
        if (Files.exists(target) && (!Files.isDirectory(target) || Files.list(target).use { it.findAny().isPresent })) {
            return "이미 같은 이름의 폴더가 있습니다: $folder"
        }
        return null
    }

    /**
     * Creates the folder structure on disk. Everything except the optional Git step is
     * all-or-nothing: on failure the partially created folder is removed and the exception is
     * rethrown. A Git failure only produces a warning - the project is already usable.
     */
    fun create(request: NewProjectRequest): CreatedProject {
        validate(request)?.let { throw IllegalArgumentException(it) }
        val root = Paths.get(request.location.trim()).resolve(folderNameFor(request.title))
        val existed = Files.exists(root)
        try {
            Files.createDirectories(root)
            val manifest = ProjectManifest(
                title = request.title.trim(),
                author = request.author.trim(),
                createdAt = Instant.now().toString(),
            )
            Files.writeString(root.resolve(Project.MANIFEST_FILE), manifest.toJson())
            for (dir in listOf(Project.MANUSCRIPT_DIR, Project.CHARACTERS_DIR, Project.WORLD_DIR)) {
                Files.createDirectories(root.resolve(dir))
            }
            Files.writeString(root.resolve(Project.MANUSCRIPT_DIR).resolve(FIRST_CHAPTER), "# 제1화\n\n")
            Files.writeString(root.resolve(Project.USER_DICTIONARY), "{\n  \"words\": []\n}\n")
            Files.writeString(root.resolve(".gitignore"), ".texted/\n")

            val warnings = if (request.initGit) initGit(root, manifest) else emptyList()
            return CreatedProject(Project.of(root, manifest), warnings)
        } catch (e: Exception) {
            if (!existed) deleteRecursively(root)
            throw e
        }
    }

    private fun initGit(root: Path, manifest: ProjectManifest): List<String> = try {
        Git.init().setDirectory(root.toFile()).setInitialBranch("main").call().use { git ->
            git.add().addFilepattern(".").call()
            val author = PersonIdent(manifest.author.ifBlank { "Texted" }, "texted@localhost")
            git.commit()
                .setMessage("프로젝트 생성: ${manifest.title}")
                .setAuthor(author).setCommitter(author)
                .setSign(false)
                .call()
        }
        emptyList()
    } catch (e: Exception) {
        listOf("Git 저장소를 만들지 못했습니다: ${e.message ?: e}")
    }

    private fun deleteRecursively(path: Path) {
        runCatching {
            Files.walk(path).use { stream -> stream.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }
}
