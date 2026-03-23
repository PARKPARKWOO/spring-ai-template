package com.example.demo.dto

import com.example.demo.model.Vendor
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("AiCallContext")
class AiCallContextTest {

    @Nested
    @DisplayName("from() 팩토리 메서드")
    inner class FromFactory {

        @Test
        @DisplayName("AiApiRequest에서 AiCallContext를 올바르게 생성한다")
        fun `creates context from request with all fields`() {
            val request = AiApiRequest(
                applicationId = "app-1",
                models = listOf(
                    ModelSpec(
                        vendor = Vendor.ANTHROPIC,
                        version = "claude-sonnet-4-5",
                        vendorOptions = VendorOptions.AnthropicOptions(
                            cacheStrategy = "SYSTEM_ONLY",
                        ),
                    )
                ),
                messages = listOf(
                    SystemChatMessage(content = "당신은 이력서 컨설턴트입니다."),
                    UserChatMessage(content = "이 이력서를 리뷰해주세요."),
                ),
                sessionId = "session-1",
                responseSchema = """{"type":"object","properties":{"score":{"type":"integer"}}}""",
                timeoutSeconds = 180,
                maxTokens = 4000,
                requestType = "review",
            )

            val modelSpec = request.models.first()
            val context = AiCallContext.from(request, "app-1", modelSpec, request.responseSchema)

            assertEquals(2, context.messages.size)
            assertEquals("app-1", context.applicationId)
            assertEquals("session-1", context.sessionId)
            assertEquals(4000, context.maxTokens)
            assertEquals("claude-sonnet-4-5", context.model)
            assertEquals("review", context.requestType)
            assertEquals(180, context.timeoutSeconds)
            assertNotNull(context.jsonSchema)
            assertTrue(context.vendorOptions is VendorOptions.AnthropicOptions)
        }

        @Test
        @DisplayName("maxTokens가 없으면 null로 설정된다")
        fun `maxTokens is null when not provided`() {
            val request = AiApiRequest(
                applicationId = "app-1",
                models = listOf(ModelSpec(vendor = Vendor.GOOGLE, version = "gemini-2.5-flash")),
                messages = listOf(UserChatMessage(content = "test")),
                sessionId = "s1",
            )
            val context = AiCallContext.from(request, "app-1", request.models.first(), null)

            assertNull(context.maxTokens)
            assertNull(context.jsonSchema)
            assertNull(context.requestType)
        }

        @Test
        @DisplayName("Gemini 벤더 옵션이 올바르게 전달된다")
        fun `gemini vendor options are preserved`() {
            val geminiOptions = VendorOptions.GeminiOptions(
                urlContexts = listOf("https://example.com"),
                enableGoogleSearch = true,
                toolNames = listOf("weatherTool"),
                useCachedContent = true,
                cachedContentName = "cachedContent/test",
            )
            val request = AiApiRequest(
                applicationId = "app-1",
                models = listOf(
                    ModelSpec(vendor = Vendor.GOOGLE, version = "gemini-2.5-flash", vendorOptions = geminiOptions)
                ),
                messages = listOf(UserChatMessage(content = "test")),
                sessionId = "s1",
            )

            val context = AiCallContext.from(request, "app-1", request.models.first(), null)
            val opts = context.vendorOptions as VendorOptions.GeminiOptions

            assertEquals(listOf("https://example.com"), opts.urlContexts)
            assertEquals(true, opts.enableGoogleSearch)
            assertEquals(listOf("weatherTool"), opts.toolNames)
            assertEquals(true, opts.useCachedContent)
            assertEquals("cachedContent/test", opts.cachedContentName)
        }
    }

    @Nested
    @DisplayName("effectiveMaxTokens()")
    inner class EffectiveMaxTokens {

        @Test
        @DisplayName("maxTokens가 지정되면 해당 값을 반환한다")
        fun `returns specified maxTokens`() {
            val context = createContext(maxTokens = 8000)
            assertEquals(8000, context.effectiveMaxTokens())
        }

        @Test
        @DisplayName("maxTokens가 null이면 기본값 2000을 반환한다")
        fun `returns default when maxTokens is null`() {
            val context = createContext(maxTokens = null)
            assertEquals(AiCallContext.DEFAULT_MAX_TOKENS, context.effectiveMaxTokens())
            assertEquals(2000, context.effectiveMaxTokens())
        }
    }

    @Nested
    @DisplayName("effectiveTimeoutMs()")
    inner class EffectiveTimeoutMs {

        @Test
        @DisplayName("timeoutSeconds가 지정되면 밀리초로 변환한다")
        fun `converts specified timeout to milliseconds`() {
            val context = createContext(timeoutSeconds = 180)
            assertEquals(180_000L, context.effectiveTimeoutMs())
        }

        @Test
        @DisplayName("timeoutSeconds가 null이면 기본값 120초(120000ms)를 반환한다")
        fun `returns default 120 seconds when null`() {
            val context = createContext(timeoutSeconds = null)
            assertEquals(120_000L, context.effectiveTimeoutMs())
        }
    }

    private fun createContext(
        maxTokens: Int? = null,
        timeoutSeconds: Int? = null,
    ) = AiCallContext(
        messages = listOf(UserChatMessage(content = "test")),
        applicationId = "app-test",
        sessionId = "session-test",
        maxTokens = maxTokens,
        timeoutSeconds = timeoutSeconds,
    )
}
