package io.github.nd2026.edited.project

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Contents of a project's `texted.project.json`: the identity of a work, independent of where
 * the folder lives. Hand-(de)serialized, like [io.github.nd2026.edited.lexer.UserDictionaryLoader],
 * so the on-disk format stays an explicit, author-readable contract.
 */
data class ProjectManifest(
    val title: String,
    val author: String = "",
    val language: String = "ko",
    /** ISO-8601 instant of creation. */
    val createdAt: String,
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
) {
    fun toJson(): String = PRETTY.encodeToString(JsonObject.serializer(), buildJsonObject {
        put("formatVersion", formatVersion)
        put("title", title)
        put("author", author)
        put("language", language)
        put("createdAt", createdAt)
    })

    companion object {
        const val CURRENT_FORMAT_VERSION = 1
        private val PRETTY = Json { prettyPrint = true }

        fun fromJson(text: String): ProjectManifest {
            val obj = Json.parseToJsonElement(text).jsonObject
            fun string(key: String) = obj[key]?.jsonPrimitive?.contentOrNull
            val version = obj["formatVersion"]?.jsonPrimitive?.int ?: CURRENT_FORMAT_VERSION
            require(version <= CURRENT_FORMAT_VERSION) { "프로젝트 형식 버전 $version 은(는) 지원되지 않습니다." }
            return ProjectManifest(
                title = requireNotNull(string("title")) { "texted.project.json: title 이 없습니다." },
                author = string("author").orEmpty(),
                language = string("language") ?: "ko",
                createdAt = string("createdAt").orEmpty(),
                formatVersion = version,
            )
        }
    }
}
