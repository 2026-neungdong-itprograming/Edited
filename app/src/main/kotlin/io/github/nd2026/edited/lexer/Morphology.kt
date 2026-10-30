package io.github.nd2026.edited.lexer

import io.github.nd2026.edited.core.Parallel

/**
 * Shared entry point for features that need morphemes outside the editor (inspections, statistics,
 * search, export, history). Lead-owned; tasks only call it. Uses the same Kiwi instance as the
 * editor ([DefaultMorphemeAnalyzer]), loaded on first use - possibly downloading the model - so
 * call [warmUp] early and expect the very first [analyze] to be slow.
 *
 * Kiwi tags follow the Sejong tag set. Examples (surface/tag):
 * - "민준이가 밥을 먹었다." → 민준/NNP 이/XSN 가/JKS 밥/NNG 을/JKO 먹/VV 었/EP 다/EF ./SF
 * - "아름다웠다" → 아름답/VA 었/EP 다/EF (predicates come back as stems; see [lemma])
 * - "열려졌다" → 열리/VV 어/EC 지/VX 었/EP 다/EF
 */
object Morphology {
    /** False when Kiwi could not be loaded (no model, offline first run…); callers should then skip morpheme features. */
    val isAvailable: Boolean get() = DefaultMorphemeAnalyzer.isAvailable

    /**
     * Morphemes of [text]; offsets index [text]. Empty if Kiwi is unavailable. Safe to call from any thread.
     *
     * Results are memoized per text (inspections, statistics, search and tags all re-read the same
     * manuscript lines), and the memo is dropped whenever the project dictionary reloads Kiwi.
     */
    fun analyze(text: String): List<Morpheme> {
        if (text.isEmpty()) return emptyList()
        val generation = DefaultMorphemeAnalyzer.generation
        val shard = shardOf(text)
        synchronized(shard) {
            if (shard.generation != generation) {
                shard.clear()
                shard.generation = generation
            }
            shard[text]?.let { return it }
        }
        val morphemes = DefaultMorphemeAnalyzer.analyze(text)
        // Don't memoize "Kiwi unavailable" or a result computed against a dictionary that was just replaced.
        if (DefaultMorphemeAnalyzer.isAvailable && text.length <= MAX_CACHED_TEXT) synchronized(shard) {
            if (shard.generation == generation) shard[text] = morphemes
        }
        return morphemes
    }

    /**
     * Morphemes per line, each line's offsets relative to that line (matches `InspectionRule`'s 0-based lines).
     * Lines are analyzed in parallel on [Parallel.pool]; the result order always matches [lines].
     */
    fun analyzeLines(lines: List<String>): List<List<Morpheme>> =
        Parallel.map(lines, minChunk = LINES_PER_TASK) { if (it.isBlank()) emptyList() else analyze(it) }

    /**
     * Analyzes every line of [texts] in parallel so later [analyze]/[analyzeLines] calls - even
     * single-threaded ones on the EDT - are memo hits. Blocks until done; call it off the EDT.
     */
    fun prefetch(texts: Collection<String>) {
        if (!isAvailable) return
        val lines = texts.flatMap { it.split('\n') }.map { it.removeSuffix("\r") }.filter { it.isNotBlank() }.distinct()
        Parallel.forEach(lines, minChunk = LINES_PER_TASK) { analyze(it) }
    }

    /** [analyze] as a [MorphemeAnalyzer], for editors: reopening a tab re-reads lines from the memo instead of Kiwi. */
    val analyzer: MorphemeAnalyzer = object : MorphemeAnalyzer {
        override fun analyze(text: String, baseOffset: Int): List<Morpheme> {
            val morphemes = this@Morphology.analyze(text)
            return if (baseOffset == 0) morphemes else morphemes.map { it.copy(start = it.start + baseOffset, end = it.end + baseOffset) }
        }
        override val isThreadSafe: Boolean get() = true
    }

    /** Waits for a pending character-name dictionary rebuild; see [DefaultMorphemeAnalyzer.awaitDictionary]. Off the EDT only. */
    fun awaitDictionary() = DefaultMorphemeAnalyzer.awaitDictionary()

    /** Drops memoized results; the next [analyze] calls go to Kiwi again. */
    fun clearCache() = shards.forEach { synchronized(it) { it.clear() } }

    private const val CACHE_ENTRIES = 50_000
    private const val MAX_CACHED_TEXT = 4_000
    /** Lines handed to one worker at a time: a manuscript line (paragraph) takes Kiwi ~0.1-1 ms. */
    private const val LINES_PER_TASK = 8

    /**
     * LRU memo of [analyze], keyed by the exact text and split into lock-striped shards by hash, so
     * [Parallel.parallelism] workers hitting the memo at once rarely wait on each other. Each shard is guarded by itself.
     */
    private class Shard(private val capacity: Int) : LinkedHashMap<String, List<Morpheme>>(256, 0.75f, true) {
        var generation = -1L
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Morpheme>>?) = size > capacity
    }

    private val shards: Array<Shard> = run {
        val count = Integer.highestOneBit((Parallel.parallelism * 4).coerceIn(4, 64) * 2 - 1)
        Array(count) { Shard(CACHE_ENTRIES / count) }
    }

    private fun shardOf(text: String): Shard {
        val h = text.hashCode()
        return shards[(h xor (h ushr 16)) and (shards.size - 1)]
    }

    /** Loads Kiwi on a background thread so the first real call doesn't stall the UI. */
    fun warmUp() {
        Thread({ runCatching { analyze("준비") } }, "kiwi-warmup").apply { isDaemon = true }.start()
    }
}

private val NOUN_TAGS = setOf("NNG", "NNP", "NNB", "NP", "NR")
private val PREDICATE_TAGS = setOf("VV", "VA", "VX", "VCP", "VCN")
private val CONTENT_TAGS = setOf("NNG", "NNP", "VV", "VA", "MAG")
private val LEMMA_TAGS = setOf("VV", "VA", "VX")

/** Common/proper/bound noun, pronoun or numeral. */
val Morpheme.isNoun: Boolean get() = partOfSpeech in NOUN_TAGS

/** Proper noun (NNP): names of people and places, once they are in the user dictionary. */
val Morpheme.isProperNoun: Boolean get() = partOfSpeech == "NNP"

/** Verb, adjective, auxiliary or copula stem. */
val Morpheme.isPredicate: Boolean get() = partOfSpeech in PREDICATE_TAGS

/** Particle (조사): JKS 이/가, JKO 을/를, JX 은/는, JKB 에/에서 … */
val Morpheme.isParticle: Boolean get() = partOfSpeech.startsWith("J")

/** Ending (어미): EP 었/겠, EF 다/요 (sentence-final), EC 고/어 (connective), ETM/ETN. */
val Morpheme.isEnding: Boolean get() = partOfSpeech.startsWith("E")

/** Adverb (MAG 갑자기/매우, MAJ 그리고/그러나). */
val Morpheme.isAdverb: Boolean get() = partOfSpeech == "MAG" || partOfSpeech == "MAJ"

/** A word that carries meaning (noun, verb, adjective, adverb) - what word counts and repetition checks want. */
val Morpheme.isContentWord: Boolean
    get() = partOfSpeech in CONTENT_TAGS

/** Dictionary form: verb/adjective stems get "다" (먹 → 먹다, 아름답 → 아름답다); everything else is its surface. */
val Morpheme.lemma: String get() = if (partOfSpeech in LEMMA_TAGS) surface + "다" else surface
