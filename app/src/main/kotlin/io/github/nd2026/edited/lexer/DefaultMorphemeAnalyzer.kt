package io.github.nd2026.edited.lexer

import java.nio.file.Files
import java.nio.file.Paths

/**
 * Process-wide [MorphemeAnalyzer] that every [io.github.nd2026.edited.core.TextArea] editor uses
 * by default. Kiwi (and its language model) is loaded lazily on the first [analyze] call - which
 * [MorphemeIndex] makes on its background thread, so the EDT never waits on model loading - and
 * shared, since the model is large and the native handle is thread-safe.
 *
 * If Kiwi can't be loaded (missing model, unsupported platform) the failure is reported once and
 * the editor keeps working as a plain text editor: [analyze] returns no morphemes.
 */
object DefaultMorphemeAnalyzer : MorphemeAnalyzer {

    /** The author's local dictionary, read from the working directory if present. */
    private const val USER_DICTIONARY = "custom-rules.json"

    private val delegate: KiwiMorphemeAnalyzer? by lazy {
        try {
            val dictionary = Paths.get(USER_DICTIONARY)
            val words = if (Files.isRegularFile(dictionary)) UserDictionaryLoader.load(dictionary) else emptyList()
            KiwiMorphemeAnalyzer(userWords = words)
        } catch (e: Throwable) {
            System.err.println("Kiwi morpheme analysis disabled: ${e.message ?: e}")
            null
        }
    }

    override fun analyze(text: String, baseOffset: Int): List<Morpheme> =
        delegate?.analyze(text, baseOffset) ?: emptyList()
}
