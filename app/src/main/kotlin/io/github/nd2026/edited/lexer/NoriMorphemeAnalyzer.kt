package io.github.nd2026.edited.lexer

import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.Tokenizer
import org.apache.lucene.analysis.ko.KoreanTokenizer
import org.apache.lucene.analysis.ko.dict.UserDictionary
import org.apache.lucene.analysis.ko.tokenattributes.PartOfSpeechAttribute
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute
import org.apache.lucene.util.AttributeFactory
import java.io.StringReader

/**
 * [MorphemeAnalyzer] backed by Lucene's Nori [KoreanTokenizer]. Builds a bare tokenizer with no
 * `KoreanPartOfSpeechStopFilter` on top - unlike the stock `KoreanAnalyzer`, which drops
 * particles/endings by default for search relevance - because the linter (repeated-ending
 * detection, etc.) needs every morpheme, not just the "search-relevant" ones.
 *
 * Not safe for concurrent [analyze] calls on the same instance; callers that need concurrency
 * should use one instance per thread ([MorphemeIndex] confines this to a single background
 * thread for exactly that reason).
 */
class NoriMorphemeAnalyzer(
    userDictionary: UserDictionary? = null,
    // MIXED keeps both a compound and its decomposed parts in the stream, as *overlapping*
    // tokens (confirmed empirically - see NoriMorphemeAnalyzerTest) - great for search recall,
    // but it breaks the "one contiguous, non-overlapping token per span" stream a linter needs.
    // DISCARD keeps only the decomposed parts, so the stream stays flat and contiguous.
    private val mode: KoreanTokenizer.DecompoundMode = KoreanTokenizer.DecompoundMode.DISCARD,
    /** Keep sentence-final punctuation (`.`/`!`/`?`) as its own SF token instead of dropping
     *  it - the novel editor's sentence-level diff needs to see sentence boundaries. */
    private val discardPunctuation: Boolean = false,
    private val outputUnknownUnigrams: Boolean = false,
) : MorphemeAnalyzer, AutoCloseable {

    private var analyzer: Analyzer = buildAnalyzer(userDictionary)

    /** Rebuilds the underlying analyzer against a new/updated user dictionary. */
    fun reload(userDictionary: UserDictionary?) {
        analyzer.close()
        analyzer = buildAnalyzer(userDictionary)
    }

    override fun analyze(text: String, baseOffset: Int): List<Morpheme> {
        if (text.isEmpty()) return emptyList()

        val morphemes = mutableListOf<Morpheme>()
        analyzer.tokenStream(FIELD_NAME, StringReader(text)).use { stream ->
            val termAtt = stream.addAttribute(CharTermAttribute::class.java)
            val offsetAtt = stream.addAttribute(OffsetAttribute::class.java)
            val posAtt = stream.addAttribute(PartOfSpeechAttribute::class.java)

            stream.reset()
            while (stream.incrementToken()) {
                val start = baseOffset + offsetAtt.startOffset()
                morphemes += Morpheme(
                    surface = termAtt.toString(),
                    start = start,
                    end = baseOffset + offsetAtt.endOffset(),
                    partOfSpeech = (posAtt.leftPOS ?: posAtt.rightPOS)?.name ?: "UNKNOWN",
                    decompound = decompound(posAtt, start),
                )
            }
            stream.end()
        }
        return morphemes
    }

    /** Nori exposes a compound's parts as bare (POS, surface) pairs with no offsets of their
     *  own, so we lay them out left-to-right starting at the compound's own start offset. */
    private fun decompound(posAtt: PartOfSpeechAttribute, compoundStart: Int): List<Morpheme> {
        val parts = posAtt.morphemes ?: return emptyList()
        var offset = compoundStart
        return parts.map { part ->
            val end = offset + part.surfaceForm.length
            Morpheme(part.surfaceForm, offset, end, part.posTag.name).also { offset = end }
        }
    }

    override fun close() = analyzer.close()

    private fun buildAnalyzer(userDictionary: UserDictionary?): Analyzer =
        object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer: Tokenizer = KoreanTokenizer(
                    AttributeFactory.DEFAULT_ATTRIBUTE_FACTORY,
                    userDictionary,
                    mode,
                    outputUnknownUnigrams,
                    discardPunctuation,
                )
                return TokenStreamComponents(tokenizer)
            }
        }

    private companion object {
        const val FIELD_NAME = "body"
    }
}
