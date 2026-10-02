package io.github.nd2026.edited.lexer

import io.github.nd2026.edited.lexer.KiwiMorphemeAnalyzer.UserWord
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path

/**
 * Loads the author's local `custom-rules.json` (see README.md) into the [UserWord]s
 * [KiwiMorphemeAnalyzer] registers with Kiwi, so the JSON schema stays author-facing.
 *
 * Example `custom-rules.json`:
 * ```json
 * { "words": [
 *     { "surface": "산왕고" },
 *     { "surface": "은빛 열쇠", "pos": "NNG", "score": 3.0 }
 * ] }
 * ```
 * An entry without `pos` is registered as a proper noun (`NNP`) - the case README describes as
 * preventing false-positive splitting of character/place names. Kiwi decides for itself how a
 * registered word is segmented, so no per-entry segmentation is accepted.
 */
object UserDictionaryLoader {

    /** Returns an empty list if the file declares no words. */
    fun load(path: Path): List<UserWord> {
        val words = Json.parseToJsonElement(Files.readString(path))
            .jsonObject["words"]?.jsonArray ?: return emptyList()
        return words.map { parseWord(it.jsonObject) }
    }

    private fun parseWord(entry: JsonObject): UserWord {
        val surface = requireNotNull(entry["surface"]?.jsonPrimitive?.contentOrNull) {
            "custom-rules.json: every word needs a \"surface\""
        }
        return UserWord(
            surface = surface,
            partOfSpeech = entry["pos"]?.jsonPrimitive?.contentOrNull ?: "NNP",
            score = entry["score"]?.jsonPrimitive?.floatOrNull ?: 0f,
        )
    }
}
