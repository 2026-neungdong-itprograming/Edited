package io.github.nd2026.edited.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Properties

/**
 * Where an OpenAI-compatible Chat Completions endpoint lives (e.g. an llms route,
 * `https://llms.goodtek.xyz/r/<slug>/v1`) and which model to ask.
 */
data class LlmConfig(val baseUrl: String, val apiKey: String, val model: String = DEFAULT_MODEL) {
    companion object {
        const val DEFAULT_MODEL = "claude-sonnet-5-5"

        /** Per-user settings file; keeps the key out of the project folder (which is a Git repository). */
        val userFile: Path = Path.of(System.getProperty("user.home"), ".texted", "llm.properties")

        /**
         * Reads `TEXTED_LLM_BASE_URL` / `TEXTED_LLM_API_KEY` / `TEXTED_LLM_MODEL`, falling back to the
         * `OPENAI_BASE_URL` / `OPENAI_API_KEY` / `OPENAI_MODEL` that `llms env <slug>` exports, then to
         * [userFile] (`base_url=`, `api_key=`, `model=`). Null when no endpoint is configured.
         */
        fun load(env: Map<String, String> = System.getenv(), file: Path = userFile): LlmConfig? {
            val props = Properties()
            if (Files.isRegularFile(file)) runCatching { Files.newBufferedReader(file).use(props::load) }
            fun pick(vararg keys: String): String? =
                keys.firstNotNullOfOrNull { key -> (env[key] ?: props.getProperty(key))?.trim()?.takeIf(String::isNotEmpty) }
            val baseUrl = pick("TEXTED_LLM_BASE_URL", "OPENAI_BASE_URL", "base_url") ?: return null
            val apiKey = pick("TEXTED_LLM_API_KEY", "OPENAI_API_KEY", "api_key") ?: return null
            return LlmConfig(baseUrl.trimEnd('/'), apiKey, pick("TEXTED_LLM_MODEL", "OPENAI_MODEL", "model") ?: DEFAULT_MODEL)
        }
    }
}

/** Minimal blocking client for `POST {baseUrl}/chat/completions`. Call it off the EDT. */
class LlmClient(
    val config: LlmConfig,
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
) {
    data class Message(val role: String, val content: String)

    class LlmException(message: String) : RuntimeException(message)

    /** Sends [messages] and returns the first choice's text. */
    fun chat(messages: List<Message>, temperature: Double = 0.4): String {
        val body = buildJsonObject {
            put("model", config.model)
            put("temperature", temperature)
            putJsonArray("messages") {
                for (m in messages) addJsonObject { put("role", m.role); put("content", m.content) }
            }
        }
        val request = HttpRequest.newBuilder(URI.create("${config.baseUrl}/chat/completions"))
            .timeout(Duration.ofSeconds(90))
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) throw LlmException("HTTP ${response.statusCode()}: ${response.body().take(200)}")
        return parseContent(response.body())
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** `choices[0].message.content` of a Chat Completions response. */
        fun parseContent(body: String): String {
            val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrElse { throw LlmException("응답을 읽을 수 없습니다") }
            (root["error"] as? JsonObject)?.let { throw LlmException(it["message"]?.jsonPrimitive?.content ?: it.toString()) }
            return runCatching { root["choices"]!!.jsonArray[0].jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive.content }
                .getOrElse { throw LlmException("응답에 내용이 없습니다") }
        }
    }
}
