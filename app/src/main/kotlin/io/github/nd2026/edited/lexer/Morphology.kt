package io.github.nd2026.edited.lexer

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

    /** Morphemes of [text]; offsets index [text]. Empty if Kiwi is unavailable. */
    fun analyze(text: String): List<Morpheme> = DefaultMorphemeAnalyzer.analyze(text)

    /** Morphemes per line, each line's offsets relative to that line (matches `InspectionRule`'s 0-based lines). */
    fun analyzeLines(lines: List<String>): List<List<Morpheme>> = lines.map { if (it.isBlank()) emptyList() else analyze(it) }

    /** Loads Kiwi on a background thread so the first real call doesn't stall the UI. */
    fun warmUp() {
        Thread({ runCatching { analyze("준비") } }, "kiwi-warmup").apply { isDaemon = true }.start()
    }
}

private val NOUN_TAGS = setOf("NNG", "NNP", "NNB", "NP", "NR")
private val PREDICATE_TAGS = setOf("VV", "VA", "VX", "VCP", "VCN")

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
    get() = partOfSpeech in setOf("NNG", "NNP", "VV", "VA", "MAG")

/** Dictionary form: verb/adjective stems get "다" (먹 → 먹다, 아름답 → 아름답다); everything else is its surface. */
val Morpheme.lemma: String get() = if (partOfSpeech in setOf("VV", "VA", "VX")) surface + "다" else surface
