package com.example.demo.adapter.out.client

import com.example.demo.adapter.out.persistence.ApiKeyRepository
import com.example.demo.business.TokenizerService
import com.example.demo.dto.AiCallContext
import com.example.demo.dto.SystemChatMessage
import com.example.demo.dto.UserChatMessage
import com.example.demo.dto.VendorOptions
import com.example.demo.model.Vendor
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.ai.anthropic.AnthropicChatOptions
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage

@DisplayName("AnthropicClient")
class AnthropicClientTest {

    private lateinit var client: AnthropicClient
    private val tokenizerService = mockk<TokenizerService>(relaxed = true)
    private val apiKeyRepository = mockk<ApiKeyRepository>(relaxed = true)

    @BeforeEach
    fun setUp() {
        client = AnthropicClient(tokenizerService, apiKeyRepository)
    }

    @Test
    @DisplayName("벤더가 ANTHROPIC이다")
    fun `vendor is ANTHROPIC`() {
        assertEquals(Vendor.ANTHROPIC, client.getVendor())
    }

    @Nested
    @DisplayName("generatePrompt()")
    inner class GeneratePrompt {

        @Test
        @DisplayName("기본 maxTokens로 프롬프트를 생성한다")
        fun `creates prompt with default maxTokens`() {
            val context = createContext()
            val messages = listOf(
                SystemMessage("시스템 프롬프트"),
                UserMessage("사용자 메시지"),
            )

            val prompt = invokeGeneratePrompt(context, messages)
            val options = prompt.options as AnthropicChatOptions

            assertEquals(AiCallContext.DEFAULT_MAX_TOKENS, options.maxTokens)
            assertEquals(2, prompt.instructions.size)
        }

        @Test
        @DisplayName("사용자 지정 maxTokens로 프롬프트를 생성한다")
        fun `creates prompt with custom maxTokens`() {
            val context = createContext(maxTokens = 8000)
            val messages = listOf(UserMessage("이력서 리뷰해주세요"))

            val prompt = invokeGeneratePrompt(context, messages)
            val options = prompt.options as AnthropicChatOptions

            assertEquals(8000, options.maxTokens)
        }

        @Test
        @DisplayName("모델 버전을 올바르게 설정한다")
        fun `sets model version correctly`() {
            val context = createContext(model = "claude-sonnet-4-5")
            val messages = listOf(UserMessage("test"))

            val prompt = invokeGeneratePrompt(context, messages)
            val options = prompt.options as AnthropicChatOptions

            assertEquals("claude-sonnet-4-5", options.model)
        }

        @Test
        @DisplayName("모델 버전이 없으면 null로 유지한다")
        fun `model is null when not specified`() {
            val context = createContext(model = null)
            val messages = listOf(UserMessage("test"))

            val prompt = invokeGeneratePrompt(context, messages)
            val options = prompt.options as AnthropicChatOptions

            assertNull(options.model)
        }

        @Test
        @DisplayName("캐시 전략 옵션을 올바르게 설정한다")
        fun `sets cache strategy options`() {
            val anthropicOptions = VendorOptions.AnthropicOptions(
                cacheStrategy = "SYSTEM_ONLY",
            )
            val context = createContext(vendorOptions = anthropicOptions)
            val messages = listOf(UserMessage("test"))

            val prompt = invokeGeneratePrompt(context, messages)
            val options = prompt.options as AnthropicChatOptions

            assertNotNull(options.cacheOptions)
        }

        @Test
        @DisplayName("벤더 옵션이 없어도 정상 동작한다")
        fun `works without vendor options`() {
            val context = createContext(vendorOptions = null)
            val messages = listOf(UserMessage("test"))

            val prompt = invokeGeneratePrompt(context, messages)
            val options = prompt.options as AnthropicChatOptions

            assertNotNull(options)
            assertNull(options.cacheOptions)
        }

        @Test
        @DisplayName("다른 벤더의 옵션이 들어와도 무시한다")
        fun `ignores non-anthropic vendor options`() {
            val geminiOptions = VendorOptions.GeminiOptions(
                enableGoogleSearch = true,
            )
            val context = createContext(vendorOptions = geminiOptions)
            val messages = listOf(UserMessage("test"))

            val prompt = invokeGeneratePrompt(context, messages)
            val options = prompt.options as AnthropicChatOptions

            // Gemini 옵션은 무시됨 - Anthropic 옵션만 적용
            assertNull(options.cacheOptions)
        }
    }

    @Nested
    @DisplayName("메시지 변환")
    inner class MessageConversion {

        @Test
        @DisplayName("ChatMessage를 Spring AI Message로 올바르게 변환한다 (generatePrompt 통해 검증)")
        fun `converts chat messages correctly via prompt generation`() {
            // protected convertToSpringAiMessages는 generatePrompt 내부에서 호출됨
            // prompt의 instructions를 통해 변환 결과를 간접 검증
            val context = AiCallContext(
                messages = listOf(
                    SystemChatMessage("시스템"),
                    UserChatMessage("사용자"),
                ),
                applicationId = "app-test",
                sessionId = "s1",
            )
            val messages = listOf(
                SystemMessage("시스템"),
                UserMessage("사용자"),
            )

            val prompt = invokeGeneratePrompt(context, messages)

            assertEquals(2, prompt.instructions.size)
            assertTrue(prompt.instructions[0] is SystemMessage)
            assertTrue(prompt.instructions[1] is UserMessage)
            assertEquals("시스템", prompt.instructions[0].text)
            assertEquals("사용자", prompt.instructions[1].text)
        }
    }

    private fun createContext(
        maxTokens: Int? = null,
        model: String? = null,
        vendorOptions: VendorOptions? = null,
        jsonSchema: String? = null,
    ) = AiCallContext(
        messages = listOf(UserChatMessage("test")),
        applicationId = "app-test",
        sessionId = "session-test",
        maxTokens = maxTokens,
        model = model,
        vendorOptions = vendorOptions,
        jsonSchema = jsonSchema,
    )

    /**
     * protected generatePrompt를 테스트하기 위해 리플렉션 사용
     */
    private fun invokeGeneratePrompt(
        context: AiCallContext,
        messages: List<org.springframework.ai.chat.messages.Message>,
    ): org.springframework.ai.chat.prompt.Prompt {
        val method = AnthropicClient::class.java.getDeclaredMethod(
            "generatePrompt",
            AiCallContext::class.java,
            List::class.java,
        )
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(client, context, messages) as org.springframework.ai.chat.prompt.Prompt
    }
}
