package com.example.demo.business

import com.example.demo.adapter.out.client.AiApiFactory
import com.example.demo.adapter.out.client.AbstractAiCallTemplate
import com.example.demo.business.exception.AiServiceException
import com.example.demo.dto.*
import com.example.demo.model.ApiErrorCode
import com.example.demo.model.Vendor
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("AiService")
class AiServiceTest {

    private val aiApiFactory = mockk<AiApiFactory>()
    private lateinit var aiService: AiService

    @BeforeEach
    fun setUp() {
        aiService = AiService(aiApiFactory)
    }

    @Nested
    @DisplayName("call()")
    inner class Call {

        @Test
        @DisplayName("메시지가 비어있으면 예외를 발생시킨다")
        fun `throws when messages are empty`() = runTest {
            val request = AiApiRequest(
                applicationId = "app-1",
                models = listOf(ModelSpec(Vendor.ANTHROPIC, "claude-sonnet-4-5")),
                messages = emptyList(),
                sessionId = "s1",
            )

            val exception = assertThrows<AiServiceException> {
                aiService.call(request, "app-1")
            }
            assertEquals(ApiErrorCode.AI_SYSTEM_PROMPT_REQUIRED, exception.errorCode)
        }

        @Test
        @DisplayName("단일 벤더 호출이 정상 동작한다")
        fun `calls single vendor successfully`() = runTest {
            val mockClient = mockk<AbstractAiCallTemplate>()
            every { aiApiFactory.getClient(Vendor.ANTHROPIC) } returns mockClient
            coEvery { mockClient.call(any<AiCallContext>()) } returns AiApiResponse(
                vendor = Vendor.ANTHROPIC,
                result = "리뷰 결과입니다.",
            )

            val request = AiApiRequest(
                applicationId = "app-1",
                models = listOf(ModelSpec(Vendor.ANTHROPIC, "claude-sonnet-4-5")),
                messages = listOf(
                    SystemChatMessage(content = "당신은 이력서 컨설턴트입니다."),
                    UserChatMessage(content = "이 이력서를 리뷰해주세요."),
                ),
                sessionId = "s1",
                maxTokens = 4000,
                requestType = "review",
            )

            val responses = aiService.call(request, "app-1")

            assertEquals(1, responses.size)
            assertEquals(Vendor.ANTHROPIC, responses[0].vendor)
            assertEquals("리뷰 결과입니다.", responses[0].result)
            assertFalse(responses[0].isError)

            coVerify { mockClient.call(match<AiCallContext> {
                it.applicationId == "app-1" &&
                it.maxTokens == 4000 &&
                it.requestType == "review" &&
                it.model == "claude-sonnet-4-5"
            }) }
        }

        @Test
        @DisplayName("여러 벤더를 병렬로 호출한다")
        fun `calls multiple vendors in parallel`() = runTest {
            val anthropicClient = mockk<AbstractAiCallTemplate>()
            val geminiClient = mockk<AbstractAiCallTemplate>()

            every { aiApiFactory.getClient(Vendor.ANTHROPIC) } returns anthropicClient
            every { aiApiFactory.getClient(Vendor.GOOGLE) } returns geminiClient

            coEvery { anthropicClient.call(any<AiCallContext>()) } returns AiApiResponse(
                vendor = Vendor.ANTHROPIC,
                result = "Claude 응답",
            )
            coEvery { geminiClient.call(any<AiCallContext>()) } returns AiApiResponse(
                vendor = Vendor.GOOGLE,
                result = "Gemini 응답",
            )

            val request = AiApiRequest(
                applicationId = "app-1",
                models = listOf(
                    ModelSpec(Vendor.ANTHROPIC, "claude-sonnet-4-5"),
                    ModelSpec(Vendor.GOOGLE, "gemini-2.5-flash"),
                ),
                messages = listOf(UserChatMessage(content = "이력서 리뷰해주세요")),
                sessionId = "s1",
            )

            val responses = aiService.call(request, "app-1")

            assertEquals(2, responses.size)
            val vendors = responses.map { it.vendor }.toSet()
            assertTrue(vendors.contains(Vendor.ANTHROPIC))
            assertTrue(vendors.contains(Vendor.GOOGLE))
        }

        @Test
        @DisplayName("벤더 호출 실패 시 에러 응답을 반환한다")
        fun `returns error response when vendor call fails`() = runTest {
            val mockClient = mockk<AbstractAiCallTemplate>()
            every { aiApiFactory.getClient(Vendor.ANTHROPIC) } returns mockClient
            coEvery { mockClient.call(any<AiCallContext>()) } throws AiServiceException(
                ApiErrorCode.AI_MODEL_ERROR,
                "모델 호출 실패",
            )

            val request = AiApiRequest(
                applicationId = "app-1",
                models = listOf(ModelSpec(Vendor.ANTHROPIC, "claude-sonnet-4-5")),
                messages = listOf(UserChatMessage(content = "test")),
                sessionId = "s1",
            )

            val responses = aiService.call(request, "app-1")

            assertEquals(1, responses.size)
            assertTrue(responses[0].isError)
            assertEquals(ApiErrorCode.AI_MODEL_ERROR.name, responses[0].result)
        }

        @Test
        @DisplayName("AiCallContext에 responseSchema가 올바르게 전달된다")
        fun `passes responseSchema to AiCallContext`() = runTest {
            val mockClient = mockk<AbstractAiCallTemplate>()
            every { aiApiFactory.getClient(Vendor.GOOGLE) } returns mockClient

            val capturedContext = slot<AiCallContext>()
            coEvery { mockClient.call(capture(capturedContext)) } returns AiApiResponse(
                vendor = Vendor.GOOGLE,
                result = """{"score": 85}""",
            )

            val schema = """{"type":"object","properties":{"score":{"type":"integer"}}}"""
            val request = AiApiRequest(
                applicationId = "app-1",
                models = listOf(ModelSpec(Vendor.GOOGLE, "gemini-2.5-flash")),
                messages = listOf(UserChatMessage(content = "이력서 리뷰")),
                sessionId = "s1",
                responseSchema = schema,
            )

            aiService.call(request, "app-1")

            assertEquals(schema, capturedContext.captured.jsonSchema)
        }

        @Test
        @DisplayName("maxTokens와 requestType이 context에 올바르게 매핑된다")
        fun `maps maxTokens and requestType to context`() = runTest {
            val mockClient = mockk<AbstractAiCallTemplate>()
            every { aiApiFactory.getClient(Vendor.ANTHROPIC) } returns mockClient

            val capturedContext = slot<AiCallContext>()
            coEvery { mockClient.call(capture(capturedContext)) } returns AiApiResponse(
                vendor = Vendor.ANTHROPIC,
                result = "ok",
            )

            val request = AiApiRequest(
                applicationId = "app-1",
                models = listOf(
                    ModelSpec(Vendor.ANTHROPIC, "claude-sonnet-4-5", VendorOptions.AnthropicOptions(cacheStrategy = "SYSTEM_ONLY"))
                ),
                messages = listOf(UserChatMessage(content = "test")),
                sessionId = "s1",
                maxTokens = 8000,
                requestType = "interview",
                timeoutSeconds = 300,
            )

            aiService.call(request, "app-1")

            val ctx = capturedContext.captured
            assertEquals(8000, ctx.maxTokens)
            assertEquals("interview", ctx.requestType)
            assertEquals(300, ctx.timeoutSeconds)
            assertEquals("claude-sonnet-4-5", ctx.model)
            assertTrue(ctx.vendorOptions is VendorOptions.AnthropicOptions)
            assertEquals("SYSTEM_ONLY", (ctx.vendorOptions as VendorOptions.AnthropicOptions).cacheStrategy)
        }
    }

    @Nested
    @DisplayName("stream()")
    inner class Stream {

        @Test
        @DisplayName("모델이 비어있으면 예외를 발생시킨다")
        fun `throws when models are empty`() = runTest {
            val request = AiApiRequest(
                applicationId = "app-1",
                models = emptyList(),
                messages = listOf(UserChatMessage(content = "test")),
                sessionId = "s1",
            )

            val exception = assertThrows<AiServiceException> {
                aiService.stream(request, "app-1")
            }
            assertEquals(ApiErrorCode.AI_MODELS_EMPTY, exception.errorCode)
        }

        @Test
        @DisplayName("메시지가 비어있으면 예외를 발생시킨다")
        fun `throws when messages are empty`() = runTest {
            val request = AiApiRequest(
                applicationId = "app-1",
                models = listOf(ModelSpec(Vendor.ANTHROPIC, "claude-sonnet-4-5")),
                messages = emptyList(),
                sessionId = "s1",
            )

            val exception = assertThrows<AiServiceException> {
                aiService.stream(request, "app-1")
            }
            assertEquals(ApiErrorCode.AI_SYSTEM_PROMPT_REQUIRED, exception.errorCode)
        }
    }
}
