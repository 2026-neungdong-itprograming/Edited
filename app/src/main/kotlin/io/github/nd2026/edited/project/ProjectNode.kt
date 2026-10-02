package io.github.nd2026.edited.project

import java.nio.file.Files
import java.nio.file.Path

/**
 * A node of the project tree shown in the Project pane: a snapshot of the folder structure,
 * not a live view. Directories sort before files, then both by name with numeric runs compared
 * as numbers (so `2.md` precedes `10.md`).
 */
sealed interface ProjectNode {
    val path: Path
    val name: String get() = path.fileName?.toString().orEmpty()

    data class Directory(override val path: Path, val children: List<ProjectNode>) : ProjectNode {
        /** Depth-first walk over this directory's descendants. */
        fun descendants(): Sequence<ProjectNode> = sequence {
            for (child in children) {
                yield(child)
                if (child is Directory) yieldAll(child.descendants())
            }
        }
    }

    data class File(override val path: Path) : ProjectNode {
        val isChapter: Boolean get() = name.endsWith(".md") || name.endsWith(".txt")
    }

    companion object {
        /** Folders the editor manages itself and never lists. */
        private val HIDDEN = setOf(".git", ".texted")

        fun scan(directory: Path): Directory {
            val children = Files.list(directory).use { stream ->
                stream.toList()
                    .filter { it.fileName.toString() !in HIDDEN }
                    .sortedWith(compareBy<Path> { !Files.isDirectory(it) }.then(NaturalOrder))
                    .map { if (Files.isDirectory(it)) scan(it) else File(it) }
            }
            return Directory(directory, children)
        }

        private val NaturalOrder = Comparator<Path> { a, b ->
            compareNatural(a.fileName.toString(), b.fileName.toString())
        }

        internal fun compareNatural(a: String, b: String): Int {
            var i = 0
            var j = 0
            while (i < a.length && j < b.length) {
                if (a[i].isDigit() && b[j].isDigit()) {
                    var ei = i
                    while (ei < a.length && a[ei].isDigit()) ei++
                    var ej = j
                    while (ej < b.length && b[ej].isDigit()) ej++
                    val numA = a.substring(i, ei).trimStart('0')
                    val numB = b.substring(j, ej).trimStart('0')
                    if (numA.length != numB.length) return numA.length - numB.length
                    val cmp = numA.compareTo(numB)
                    if (cmp != 0) return cmp
                    i = ei
                    j = ej
                } else {
                    val cmp = a[i].lowercaseChar().compareTo(b[j].lowercaseChar())
                    if (cmp != 0) return cmp
                    i++
                    j++
                }
            }
            return (a.length - i) - (b.length - j)
        }
    }
}
