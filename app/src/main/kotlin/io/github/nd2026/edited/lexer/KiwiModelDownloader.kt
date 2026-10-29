package io.github.nd2026.edited.lexer

import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.util.Comparator
import java.util.zip.GZIPInputStream

/**
 * Downloads the Kiwi language model from its GitHub release on first use and keeps it in a
 * per-user cache (`%LOCALAPPDATA%\Texted\kiwi\<version>` on Windows, `~/.texted/kiwi/<version>`
 * elsewhere), so nobody has to unpack it by hand. The archive is a plain `.tgz`, read here
 * without extra libraries.
 */
object KiwiModelDownloader {
    /** Must match the `kiwi-java` jar in `app/libs/`. */
    const val VERSION = "v0.24.0"

    /** Overrides the cache location; also readable from the `KIWI_MODEL_CACHE` env var. */
    const val CACHE_PROPERTY = "kiwi.model.cache"

    private val url = URI("https://github.com/bab2min/Kiwi/releases/download/$VERSION/kiwi_model_${VERSION}_base.tgz")
    private const val COMPLETE_MARKER = ".complete"

    val cacheDir: Path
        get() {
            val configured = System.getProperty(CACHE_PROPERTY) ?: System.getenv("KIWI_MODEL_CACHE")
            if (configured != null) return Paths.get(configured)
            val localAppData = System.getenv("LOCALAPPDATA")
            val base = if (localAppData != null) Paths.get(localAppData, "Texted") else Paths.get(System.getProperty("user.home"), ".texted")
            return base.resolve("kiwi").resolve(VERSION)
        }

    /** The model directory inside the cache (the archive's `models/cong/base`). */
    val modelDir: Path get() = cacheDir.resolve("models/cong/base")

    val isDownloaded: Boolean get() = Files.isRegularFile(cacheDir.resolve(COMPLETE_MARKER))

    /** Returns the cached model directory, downloading and unpacking it first if needed. Blocks; call off the EDT. */
    @Synchronized
    fun ensure(): Path {
        if (isDownloaded) return modelDir
        val cache = cacheDir
        Files.createDirectories(cache.parent)
        // Unpack beside the cache and move it in at the end, so a failed download never looks complete.
        val staging = Files.createTempDirectory(cache.parent, "kiwi-download")
        try {
            System.err.println("Downloading Kiwi model $VERSION to $cache ...")
            val client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30))
                .build()
            val response = client.send(HttpRequest.newBuilder(url).GET().build(), HttpResponse.BodyHandlers.ofInputStream())
            if (response.statusCode() != 200) {
                response.body().close()
                error("Kiwi model download failed: HTTP ${response.statusCode()} from $url")
            }
            GZIPInputStream(response.body().buffered()).use { untar(it, staging) }
            Files.createFile(staging.resolve(COMPLETE_MARKER))
            deleteRecursively(cache)
            Files.move(staging, cache, StandardCopyOption.ATOMIC_MOVE)
            System.err.println("Kiwi model ready: $cache")
            return modelDir
        } finally {
            deleteRecursively(staging)
        }
    }

    /** Extracts the regular files of a (ustar/GNU) tar stream into [target]. */
    internal fun untar(input: InputStream, target: Path) {
        val root = target.toAbsolutePath().normalize()
        val header = ByteArray(BLOCK)
        while (true) {
            if (input.readNBytes(header, 0, BLOCK) < BLOCK || header.all { it.toInt() == 0 }) return
            val name = field(header, 0, 100)
            val prefix = field(header, 345, 155)
            val size = field(header, 124, 12).trim().ifEmpty { "0" }.toLong(8)
            val type = header[156].toInt().toChar()
            val fullName = if (prefix.isEmpty()) name else "$prefix/$name"
            val padded = (size + BLOCK - 1) / BLOCK * BLOCK
            if (type == '0' || type == '\u0000') {
                val out = root.resolve(fullName).normalize()
                require(out.startsWith(root)) { "Unsafe path in Kiwi model archive: $fullName" }
                Files.createDirectories(out.parent)
                Files.newOutputStream(out).use { copy(input, it, size) }
                skip(input, padded - size)
            } else {
                skip(input, padded)
            }
        }
    }

    private fun field(header: ByteArray, offset: Int, length: Int): String {
        var end = offset
        while (end < offset + length && header[end].toInt() != 0) end++
        return String(header, offset, end - offset, Charsets.UTF_8)
    }

    private fun copy(input: InputStream, output: java.io.OutputStream, size: Long) {
        val buffer = ByteArray(64 * 1024)
        var remaining = size
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read < 0) error("Kiwi model archive is truncated")
            output.write(buffer, 0, read)
            remaining -= read
        }
    }

    private fun skip(input: InputStream, count: Long) {
        if (count > 0) input.skipNBytes(count)
    }

    private fun deleteRecursively(path: Path) {
        if (!Files.exists(path)) return
        Files.walk(path).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
    }

    private const val BLOCK = 512
}
