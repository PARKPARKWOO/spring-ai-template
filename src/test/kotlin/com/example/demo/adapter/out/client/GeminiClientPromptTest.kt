package com.example.demo.adapter.out.client

import com.example.demo.business.TokenizerService
import com.example.demo.dto.AiCallContext
import com.example.demo.dto.UserChatMessage
import com.example.demo.dto.VendorOptions
import com.example.demo.model.Vendor
import com.example.demo.common.ratelimit.ApiKeyRateLimiter
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.google.genai.GoogleGenAiChatOptions
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatModel
import org.springframework.web.client.RestClient

@DisplayName("GeminiClient - Prompt 생성")
class GeminiClientPromptTest {

    private lateinit var client: GeminiClient
    private val tokenizerService = mockk<TokenizerService>(relaxed = true)
    private val apiKeyResolver = mockk<ApiKeyResolver>(relaxed = true)
    private val rateLimiter = mockk<ApiKeyRateLimiter>(relaxed = true)
    private val vertexAi = mockk<VertexAiGeminiChatModel>(relaxed = true)
    private val urlFetchRestClient = mockk<RestClient>(relaxed = true)

    @BeforeEach
    fun setUp() {
        client = GeminiClient(tokenizerService, apiKeyResolver, rateLimiter, vertexAi, urlFetchRestClient)
    }

    @Test
    @DisplayName("벤더가 GOOGLE이다")
    fun `vendor is GOOGLE`() {
        assertEquals(Vendor.GOOGLE, client.getVendor())
    }

    @Nested
    @DisplayName("generatePrompt()")
    inner class GeneratePrompt {

        @Test
        @DisplayName("기본 maxTokens로 프롬프트를 생성한다")
        fun `creates prompt with default maxTokens`() {
            val context = createContext()
            val messages = listOf(UserMessage("test"))

            val prompt = invokeGeneratePrompt(context, messages)
            val options = prompt.options as GoogleGenAiChatOptions

            assertEquals(AiCallContext.DEFAULT_MAX_TOKENS, options.maxOutputTokens)
        }

        @Test
        @DisplayName("사용자 지정 maxTokens로 프롬프트를 생성한다")
        fun `creates prompt with custom maxTokens`() {
            val context = createContext(maxTokens = 6000)
            val messages = listOf(UserMessage("이력서를 분석해주세요"))

            val prompt = invokeGeneratePrompt(context, messages)
            val options = prompt.options as GoogleGenAiChatOptions

            assertEquals(6000, options.maxOutputTokens)
        }

        @Test
        @DisplayName("JSON Schema가 있으면 Structured Output을 설정한다")
        fun `sets structured output when jsonSchema is provided`() {
            val schema = """{"type":"object","properties":{"score":{"type":"integer"}}}"""
            val context = createContext(jsonSchema = schema)
            val messages = listOf(UserMessage("test"))

            val prompt = invokeGeneratePrompt(context, messages)
            val options = prompt.options as GoogleGenAiChatOptions

            assertEquals("application/json", options.responseMimeType)
            assertEquals(schema, options.responseSchema)
        }

        @Test
        @DisplayName("JSON Schema가 없으면 Structured Output을 설정하지 않는다")
        fun `does not set structured output when jsonSchema is null`() {
            val context = createContext(jsonSchema = null)
            val messages = listOf(UserMessage("test"))

            val prompt = invokeGeneratePrompt(context, messages)
            val options = prompt.options as GoogleGenAiChatOptions

            assertNull(options.responseMimeType)
            assertNull(options.responseSchema)
        }

        @Test
        @DisplayName("Google Search Grounding을 활성화한다")
        fun `enables google search grounding`() {
            val geminiOptions = VendorOptions.GeminiOptions(enableGoogleSearch = true)
            val context = createContext(vendorOptions = geminiOptions)
            val messages = listOf(UserMessage("test"))

            val prompt = invokeGeneratePrompt(context, messages)
            val options = prompt.options as GoogleGenAiChatOptions

            assertEquals(true, options.googleSearchRetrieval)
        }

        @Test
        @DisplayName("캐시된 콘텐츠 옵션을 올바르게 설정한다")
        fun `sets cached content options`() {
            val geminiOptions = VendorOptions.GeminiOptions(
                useCachedContent = true,
                cachedContentName = "cachedContent/test-123",
            )
            val context = createContext(vendorOptions = geminiOptions)
            val messages = listOf(UserMessage("test"))

            val prompt = invokeGeneratePrompt(context, messages)
            val options = prompt.options as GoogleGenAiChatOptions

            assertEquals(true, options.useCachedContent)
            assertEquals("cachedContent/test-123", options.cachedContentName)
        }

        @Test
        @DisplayName("모델 버전을 올바르게 설정한다")
        fun `sets model version correctly`() {
            val context = createContext(model = "gemini-2.5-flash")
            val messages = listOf(UserMessage("test"))

            val prompt = invokeGeneratePrompt(context, messages)
            val options = prompt.options as GoogleGenAiChatOptions

            assertEquals("gemini-2.5-flash", options.model)
        }

        @Test
        @DisplayName("벤더 옵션 없이도 정상 동작한다")
        fun `works without vendor options`() {
            val context = createContext(vendorOptions = null)
            val messages = listOf(UserMessage("test"))

            val prompt = invokeGeneratePrompt(context, messages)
            val options = prompt.options as GoogleGenAiChatOptions

            assertNotNull(options)
            assertEquals(AiCallContext.DEFAULT_MAX_TOKENS, options.maxOutputTokens)
        }

        @Test
        @DisplayName("Tool Names를 올바르게 설정한다")
        fun `sets tool names correctly`() {
            val geminiOptions = VendorOptions.GeminiOptions(
                toolNames = listOf("weatherFunction", "calculatorFunction"),
            )
            val context = createContext(vendorOptions = geminiOptions)
            val messages = listOf(UserMessage("test"))

            val prompt = invokeGeneratePrompt(context, messages)
            val options = prompt.options as GoogleGenAiChatOptions

            assertEquals(setOf("weatherFunction", "calculatorFunction"), options.toolNames)
        }
    }

    private fun createContext(
        maxTokens: Int? = null,
        model: String? = null,
        vendorOptions: VendorOptions? = null,
        jsonSchema: String? = null,
    ) = AiCallContext(
        messages = listOf(UserChatMessage(content = "test")),
        applicationId = "app-test",
        sessionId = "session-test",
        maxTokens = maxTokens,
        model = model,
        vendorOptions = vendorOptions,
        jsonSchema = jsonSchema,
    )

    private fun invokeGeneratePrompt(
        context: AiCallContext,
        messages: List<org.springframework.ai.chat.messages.Message>,
    ): org.springframework.ai.chat.prompt.Prompt {
        val method = GeminiClient::class.java.getDeclaredMethod(
            "generatePrompt",
            AiCallContext::class.java,
            List::class.java,
        )
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(client, context, messages) as org.springframework.ai.chat.prompt.Prompt
    }
}
