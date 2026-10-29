package io.github.nd2026.edited.vcs

import io.github.nd2026.edited.project.Project
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.TreeWalk
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

data class CommitInfo(val id: String, val message: String, val author: String, val time: Instant)

/**
 * The project's version history, kept in a Git repository in the project folder. Exported files
 * (`export/`) are never recorded - they are regenerated from the manuscript.
 */
class HistoryService(val root: Path) {
    val isRepository: Boolean get() = Files.isDirectory(root.resolve(".git"))

    /** Most recent first; empty if the project has no history yet. */
    fun log(limit: Int = 100): List<CommitInfo> {
        if (!isRepository) return emptyList()
        return Git.open(root.toFile()).use { git ->
            if (git.repository.resolve("HEAD") == null) emptyList()
            else git.log().setMaxCount(limit).call().map(::info)
        }
    }

    /** Records every change in the project (creating the repository if needed); null if nothing changed. */
    fun commitAll(message: String): CommitInfo? {
        val git = if (isRepository) Git.open(root.toFile()) else Git.init().setDirectory(root.toFile()).setInitialBranch("main").call()
        return git.use { g ->
            val names = Files.list(root).use { entries -> entries.map { it.fileName.toString() }.toList() }
            names.filter { it !in EXCLUDED }.forEach { g.add().addFilepattern(it).call() }
            // Stage deletions and edits of files that are already tracked.
            g.add().addFilepattern(".").setUpdate(true).call()
            val status = g.status().call()
            if (status.added.isEmpty() && status.changed.isEmpty() && status.removed.isEmpty()) return null
            val author = PersonIdent(authorName(), "texted@localhost")
            info(g.commit().setMessage(message).setAuthor(author).setCommitter(author).setSign(false).call())
        }
    }

    /** The content of [file] (inside [root]) as of [commitId], or null if it did not exist then. */
    fun fileAt(commitId: String, file: Path): String? {
        if (!isRepository) return null
        val relative = root.toAbsolutePath().normalize().relativize(file.toAbsolutePath().normalize()).joinToString("/")
        return Git.open(root.toFile()).use { git ->
            val repo = git.repository
            val id = repo.resolve(commitId) ?: return null
            RevWalk(repo).use { walk ->
                val tree = walk.parseCommit(id).tree
                TreeWalk.forPath(repo, relative, tree)?.use { tw -> String(repo.open(tw.getObjectId(0)).bytes, Charsets.UTF_8) }
            }
        }
    }

    private fun authorName(): String =
        runCatching { Project.open(root).manifest.author }.getOrNull()?.ifBlank { null } ?: "Texted"

    private fun info(commit: RevCommit) = CommitInfo(
        id = commit.name,
        message = commit.fullMessage.trim(),
        author = commit.authorIdent.name,
        time = Instant.ofEpochSecond(commit.commitTime.toLong()),
    )

    private companion object {
        val EXCLUDED = setOf(".git", "export")
    }
}
