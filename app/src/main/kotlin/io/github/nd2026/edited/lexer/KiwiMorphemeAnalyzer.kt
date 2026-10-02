package io.github.nd2026.edited.lexer

import kr.pe.bab2min.Kiwi
import kr.pe.bab2min.KiwiBuilder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * [MorphemeAnalyzer] backed by [Kiwi](https://github.com/bab2min/Kiwi) through its JNI binding.
 * Every morpheme Kiwi emits is kept (particles and endings included), because the linter
 * (repeated-ending detection, etc.) needs the full stream, not just the "search-relevant" part.
 *
 * Offsets always index the *original* text. Kiwi splits contracted syllables into separate
 * morphemes, so those share (or overlap on) one source span and [Morpheme.surface] can differ
 * from the source text: `했` -> `하`/XSA + `었`/EP both at the same one-character span, and
 * `그친` -> `그치`/VV (2 chars) + `ᆫ`/ETM (the final consonant, 1 char). Kiwi does no compound
 * decomposition, so [Morpheme.decompound] is always empty.
 *
 * The native handle is thread-safe for [analyze], but [reload] swaps it out, so [MorphemeIndex]'s
 * single background thread remains the intended caller.
 */
class KiwiMorphemeAnalyzer(
    private val modelDir: Path = defaultModelDir(),
    userWords: List<UserWord> = emptyList(),
) : MorphemeAnalyzer, AutoCloseable {

    /** A local user-dictionary entry - see [UserDictionaryLoader]. */
    data class UserWord(val surface: String, val partOfSpeech: String = "NNP", val score: Float = 0f)

    @Volatile
    private var kiwi: Kiwi = build(userWords)

    /** Rebuilds the underlying Kiwi instance against a new/updated user dictionary. */
    @Synchronized
    fun reload(userWords: List<UserWord>) {
        val previous = kiwi
        kiwi = build(userWords)
        previous.close()
    }

    override fun analyze(text: String, baseOffset: Int): List<Morpheme> {
        if (text.isEmpty()) return emptyList()

        return kiwi.tokenize(text, ANALYZE_OPTION).map { token ->
            val start = baseOffset + token.position
            Morpheme(
                surface = token.form,
                start = start,
                end = start + token.length,
                partOfSpeech = tagName(token.tag),
            )
        }
    }

    override fun close() = kiwi.close()

    private fun build(userWords: List<UserWord>): Kiwi {
        require(Files.isDirectory(modelDir)) {
            "Kiwi model directory not found: ${modelDir.toAbsolutePath()} (see README.md)"
        }
        nativeLibraryLoaded
        return KiwiBuilder(modelDir.toString()).use { builder ->
            for (word in userWords) {
                builder.addWord(word.surface, tagOf(word.partOfSpeech), word.score)
            }
            builder.build()
        }
    }

    companion object {
        /** Overrides the model directory location; also readable from the `KIWI_MODEL` env var. */
        const val MODEL_PROPERTY = "kiwi.model"

        private val ANALYZE_OPTION = Kiwi.AnalyzeOption(Kiwi.Match.allWithNormalizing)

        // Loaded once per JVM; Kiwi.loadLibrary falls back to unpacking the DLL bundled in the jar.
        private val nativeLibraryLoaded: Unit by lazy { Kiwi.loadLibrary() }

        // Kiwi.POSTag exposes tags only as byte constants; index them by value -> upper-case name.
        // Values 0..59 are the real tags (60+ are aliases/markers); irregular conjugation (e.g.
        // VV-I) sets the high bit on top of the base tag, so lookups mask it off.
        private val tagNames: Array<String> = Array(60) { "UNKNOWN" }.also { names ->
            for (field in Kiwi.POSTag::class.java.fields) {
                val value = field.getByte(null).toInt()
                if (value in names.indices && (value == 0 || names[value] == "UNKNOWN")) {
                    names[value] = field.name.uppercase()
                }
            }
        }

        private fun tagName(tag: Byte): String = tagNames.getOrElse(tag.toInt() and 0x7F) { "UNKNOWN" }

        private fun tagOf(name: String): Byte {
            val index = tagNames.indexOf(name.uppercase())
            require(index > 0) { "Unknown Kiwi POS tag: $name" }
            return index.toByte()
        }

        fun defaultModelDir(): Path {
            val configured = System.getProperty(MODEL_PROPERTY) ?: System.getenv("KIWI_MODEL")
            if (configured != null) return Paths.get(configured)
            return listOf("models/cong/base", "app/models/cong/base")
                .map(Paths::get)
                .firstOrNull(Files::isDirectory)
                ?: Paths.get("models/cong/base")
        }
    }
}
