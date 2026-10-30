package io.github.nd2026.edited.lexer

import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.Executors

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

    private val dictionaryWords: List<KiwiMorphemeAnalyzer.UserWord> by lazy {
        val dictionary = Paths.get(USER_DICTIONARY)
        if (Files.isRegularFile(dictionary)) runCatching { UserDictionaryLoader.load(dictionary) }.getOrDefault(emptyList()) else emptyList()
    }

    private val delegate: KiwiMorphemeAnalyzer? by lazy {
        try {
            KiwiMorphemeAnalyzer(userWords = dictionaryWords)
        } catch (e: Throwable) {
            System.err.println("Kiwi morpheme analysis disabled: ${e.message ?: e}")
            null
        }
    }

    /** Rebuilds Kiwi off the calling thread; one at a time, in order. */
    private val reloader = Executors.newSingleThreadExecutor { Thread(it, "kiwi-dictionary").apply { isDaemon = true } }

    /** Bumped every time Kiwi is rebuilt with a new dictionary, so memoized results ([Morphology]) know they are stale. */
    @Volatile
    var generation: Long = 0
        private set

    @Volatile
    private var projectWords: Set<String> = emptySet()

    /** True once Kiwi is loaded; loads it (blocking) on first access. */
    val isAvailable: Boolean get() = delegate != null

    override val isThreadSafe: Boolean get() = true

    /**
     * Blocks until every [setProjectWords] rebuild queued so far has finished (at most [timeoutSeconds]).
     * Whole-manuscript computations (character appearances, suggestions, searches) call this first so
     * they don't read the manuscript with a stale name dictionary; never call it on the EDT.
     */
    fun awaitDictionary(timeoutSeconds: Long = 30) {
        runCatching { reloader.submit {}.get(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS) }
    }

    override fun analyze(text: String, baseOffset: Int): List<Morpheme> =
        delegate?.analyze(text, baseOffset) ?: emptyList()

    /**
     * Registers the open project's names (characters and their aliases) as proper nouns, so Kiwi
     * reads "하늘이 웃었다" as 하늘/NNP rather than the common noun. Rebuilds Kiwi in the background,
     * and only when the set of names actually changed.
     */
    fun setProjectWords(names: Collection<String>) {
        val next = names.map(String::trim).filter { it.isNotEmpty() && ' ' !in it }.toSet()
        if (next == projectWords) return
        projectWords = next
        reloader.execute {
            val words = dictionaryWords + next.map { KiwiMorphemeAnalyzer.UserWord(it, "NNP", 10f) }
            runCatching { delegate?.reload(words); generation++ }.onFailure { System.err.println("Kiwi dictionary reload failed: ${it.message}") }
        }
    }
}
