package io.github.nd2026.edited.lexer

import org.apache.lucene.analysis.ko.dict.UserDictionary
//import org.json.JSONObject
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Path

/**
 * Loads the author's local `custom-rules.json` (see README.md) and converts it into the plain
 * line-based format Nori's [UserDictionary.open] expects - `표층형 [형태소1 형태소2 ...]`, one
 * entry per line - so the JSON schema stays author-facing while Nori's own loader is untouched.
 *
 * Example `custom-rules.json`:
 * ```json
 * { "words": [
 *     { "surface": "세종시", "segments": ["세종", "시"] },
 *     { "surface": "산왕고" }
 * ] }
 * ```
 * A `segments`-less entry is treated as a single, undecomposed proper noun - the case README
 * describes as preventing false-positive splitting of character/place names.
 */
//object UserDictionaryLoader {

    /** Returns null if the file declares no words, since an empty user dictionary is pointless
     *  and [UserDictionary.open]'s behavior on zero entries isn't a case worth relying on. */
//    fun load(path: Path): UserDictionary? {
//        val json = JSONObject(Files.readString(path))
//        val words = json.optJSONArray("words") ?: return null
//        if (words.isEmpty) return null
//
//        val lines = buildString {
////            for (i in 0 until words.length()) {
////                val entry = words.getJSONObject(i)
////                append(entry.getString("surface"))
////                entry.optJSONArray("segments")?.let { segments ->
////                    for (s in 0 until segments.length()) {
////                        append(' ')
////                        append(segments.getString(s))
////                    }
////                }
////                append('\n')
////            }
//        }
//        return UserDictionary.open(StringReader(lines))
//   }
//}
