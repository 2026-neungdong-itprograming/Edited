package io.github.nd2026.edited.export

import io.github.nd2026.edited.project.ChapterTitle
import io.github.nd2026.edited.project.Project
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes an EPUB 3 e-book (with an EPUB 2 `toc.ncx` for older readers) using only `java.util.zip`:
 * one XHTML page per chapter, each paragraph a `<p>`.
 */
object EpubExporter {
    fun export(project: Project, target: Path) {
        target.toAbsolutePath().parent?.let(Files::createDirectories)
        val chapters = project.chapters().mapIndexed { i, chapter ->
            val text = Files.readString(chapter.path)
            Chapter("chapter${i + 1}.xhtml", ChapterTitle.of(chapter.path, text), ChapterText.plain(text))
        }
        val title = project.title
        val author = project.manifest.author.ifBlank { "작가 미상" }
        val language = project.manifest.language
        val id = "urn:uuid:${UUID.nameUUIDFromBytes(project.root.toAbsolutePath().toString().toByteArray())}"
        val subjects = ChapterTags.ofProject(project)

        Files.newOutputStream(target).use { out ->
            ZipOutputStream(out).use { zip ->
                // The mimetype entry must come first and be stored uncompressed.
                stored(zip, "mimetype", "application/epub+zip".toByteArray())
                text(zip, "META-INF/container.xml", CONTAINER)
                text(zip, "OEBPS/content.opf", opf(id, title, author, language, subjects, chapters))
                text(zip, "OEBPS/nav.xhtml", nav(title, language, chapters))
                text(zip, "OEBPS/toc.ncx", ncx(id, title, chapters))
                text(zip, "OEBPS/style.css", STYLE)
                chapters.forEach { text(zip, "OEBPS/${it.file}", page(it, language)) }
            }
        }
    }

    private data class Chapter(val file: String, val title: String, val body: String)

    private fun stored(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        val entry = ZipEntry(name).apply {
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = bytes.size.toLong()
            crc = CRC32().apply { update(bytes) }.value
        }
        zip.putNextEntry(entry); zip.write(bytes); zip.closeEntry()
    }

    private fun text(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray()); zip.closeEntry()
    }

    internal fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun opf(id: String, title: String, author: String, language: String, subjects: List<String>, chapters: List<Chapter>): String {
        val modified = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString()
        val items = chapters.joinToString("\n") { """    <item id="${it.file.removeSuffix(".xhtml")}" href="${it.file}" media-type="application/xhtml+xml"/>""" }
        val spine = chapters.joinToString("\n") { """    <itemref idref="${it.file.removeSuffix(".xhtml")}"/>""" }
        // Keyword tags (ChapterTags.ofProject) become the book's subjects, one line each.
        val subjectLines = subjects.joinToString("") { "    <dc:subject>${escape(it)}</dc:subject>\n" }
        return """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="book-id" xml:lang="$language">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="book-id">$id</dc:identifier>
    <dc:title>${escape(title)}</dc:title>
    <dc:creator>${escape(author)}</dc:creator>
    <dc:language>$language</dc:language>
$subjectLines    <meta property="dcterms:modified">$modified</meta>
  </metadata>
  <manifest>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
    <item id="style" href="style.css" media-type="text/css"/>
$items
  </manifest>
  <spine toc="ncx">
$spine
  </spine>
</package>
"""
    }

    private fun nav(title: String, language: String, chapters: List<Chapter>): String {
        val links = chapters.joinToString("\n") { """      <li><a href="${it.file}">${escape(it.title)}</a></li>""" }
        return """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="$language" lang="$language">
<head><title>${escape(title)}</title></head>
<body>
  <nav epub:type="toc" id="toc">
    <h1>목차</h1>
    <ol>
$links
    </ol>
  </nav>
</body>
</html>
"""
    }

    private fun ncx(id: String, title: String, chapters: List<Chapter>): String {
        val points = chapters.mapIndexed { i, c ->
            """    <navPoint id="np${i + 1}" playOrder="${i + 1}"><navLabel><text>${escape(c.title)}</text></navLabel><content src="${c.file}"/></navPoint>"""
        }.joinToString("\n")
        return """<?xml version="1.0" encoding="UTF-8"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
  <head><meta name="dtb:uid" content="$id"/></head>
  <docTitle><text>${escape(title)}</text></docTitle>
  <navMap>
$points
  </navMap>
</ncx>
"""
    }

    private fun page(chapter: Chapter, language: String): String {
        // The plain text still starts with the heading line; show it as <h1> instead of a paragraph.
        val paragraphs = chapter.body.lines()
            .dropWhile { it.isBlank() }
            .let { if (it.firstOrNull()?.trim() == chapter.title) it.drop(1) else it }
            .filter { it.isNotBlank() }
            .joinToString("\n") { "  <p>${escape(it.trim())}</p>" }
        return """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="$language" lang="$language">
<head><title>${escape(chapter.title)}</title><link rel="stylesheet" type="text/css" href="style.css"/></head>
<body>
  <h1>${escape(chapter.title)}</h1>
$paragraphs
</body>
</html>
"""
    }

    private const val CONTAINER = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>
"""

    private const val STYLE = """body { line-height: 1.8; word-break: keep-all; }
h1 { font-size: 1.4em; margin: 1em 0 1.5em; }
p { margin: 0 0 0.8em; text-indent: 1em; }
"""
}
