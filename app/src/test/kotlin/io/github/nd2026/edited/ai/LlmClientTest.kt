package io.github.nd2026.edited.ai

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LlmClientTest {
    @Test
    fun configPrefersEnvironmentThenUserFile() {
        val file = Files.createTempFile("llm", ".properties")
        Files.writeString(file, "base_url=https://example.test/v1/\napi_key=sk-file\n")
        val fromFile = LlmConfig.load(env = emptyMap(), file = file)!!
        assertEquals("https://example.test/v1", fromFile.baseUrl)
        assertEquals("sk-file", fromFile.apiKey)
        assertEquals(LlmConfig.DEFAULT_MODEL, fromFile.model)

        val fromEnv = LlmConfig.load(mapOf("OPENAI_BASE_URL" to "https://env.test/v1", "OPENAI_API_KEY" to "sk-env", "OPENAI_MODEL" to "m"), file)!!
        assertEquals(LlmConfig("https://env.test/v1", "sk-env", "m"), fromEnv)

        assertNull(LlmConfig.load(env = emptyMap(), file = file.resolveSibling("missing.properties")))
    }

    @Test
    fun chatPostsMessagesAndReadsFirstChoice() {
        var seenAuth: String? = null
        var seenBody = ""
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/chat/completions") { exchange ->
            seenAuth = exchange.requestHeaders.getFirst("Authorization")
            seenBody = exchange.requestBody.readAllBytes().decodeToString()
            val reply = """{"choices":[{"message":{"role":"assistant","content":"```\n문이 열렸다.\n```"}}]}""".toByteArray()
            exchange.sendResponseHeaders(200, reply.size.toLong())
            exchange.responseBody.use { it.write(reply) }
        }
        server.start()
        try {
            val client = LlmClient(LlmConfig("http://127.0.0.1:${server.address.port}/v1", "sk-test", "test-model"))
            assertEquals("문이 열렸다.\n", ProseAssistant(client).polish("문이 열려졌다.\n"))
            assertEquals("Bearer sk-test", seenAuth)
            assertTrue("\"model\":\"test-model\"" in seenBody)
            assertTrue("문이 열려졌다." in seenBody)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun errorResponsesBecomeExceptions() {
        assertFailsWith<LlmClient.LlmException> { LlmClient.parseContent("""{"error":{"message":"quota"}}""") }
        assertFailsWith<LlmClient.LlmException> { LlmClient.parseContent("not json") }
    }
}
