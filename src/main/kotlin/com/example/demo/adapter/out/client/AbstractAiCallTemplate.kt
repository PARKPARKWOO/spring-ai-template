package com.example.demo.adapter.out.client

import com.example.demo.adapter.out.persistence.AiUsageLogsRepository
import com.example.demo.business.TokenizerService
import com.example.demo.business.exception.AiServiceException
import com.example.demo.common.ratelimit.ApiKeyRateLimiter
import com.example.demo.dto.AiApiResponse
import com.example.demo.dto.AiCallContext
import com.example.demo.dto.ChatMessage
import com.example.demo.model.AiUsageLogs
import com.example.demo.model.ApiErrorCode
import com.example.demo.model.ContentType
import com.example.demo.model.Vendor
import com.example.demo.model.api.ApiKey
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.beans.factory.annotation.Autowired
import reactor.core.publisher.Flux

/**
 * AI 벤더 호출 템플릿.
 *
 * Template Method 패턴으로 공통 흐름(메시지 변환 → API 키 조회 → 모델 생성 → 프롬프트 생성 → 호출)을
 * 정의하고, 각 벤더 클라이언트는 generateModel()과 generatePrompt()만 구현하면 된다.
 *
 * Rate limit 대응:
 * - 호출 전 사용 가능한 키 선택 (rate-limited 키 스킵)
 * - 호출 성공 시 RPM/RPD 카운트 기록
 * - 429 응답 시 해당 키 마킹 후 다른 키로 재시도 (최대 2회)
 */
abstract class AbstractAiCallTemplate(
    private val tokenizerService: TokenizerService,
    private val apiKeyResolver: ApiKeyResolver,
    private val rateLimiter: ApiKeyRateLimiter,
) : AiCallPort {

    private val log = LoggerFactory.getLogger(this::class.java)

    // 모든 벤더 클라이언트 공통 의존성. 생성자 시그니처 충돌 피하려고 필드 주입.
    @Autowired
    protected lateinit var aiUsageLogsRepository: AiUsageLogsRepository

    abstract override fun getVendor(): Vendor

    protected abstract fun generateModel(apiKey: ApiKey): ChatModel
    protected abstract fun generatePrompt(context: AiCallContext, messages: List<Message>): Prompt

    /**
     * 벤더 응답 텍스트에 대한 후처리 hook. 기본은 원문 그대로 반환.
     * Anthropic 처럼 native JSON 강제가 없는 벤더에서 ```json fence 제거 등에 사용.
     */
    protected open fun postProcessResponse(text: String, context: AiCallContext): String = text

    protected fun convertToSpringAiMessages(chatMessages: List<ChatMessage>): List<Message> =
        chatMessages.map { msg ->
            when (msg) {
                is com.example.demo.dto.UserChatMessage -> UserMessage(msg.content)
                is com.example.demo.dto.SystemChatMessage -> SystemMessage(msg.content)
                is com.example.demo.dto.AssistantChatMessage -> AssistantMessage(msg.content)
            }
        }

    protected fun calculateTokenCountFromMessages(messages: List<Message>): Int {
        val text = messages.joinToString(" ") { it.text }
        return tokenizerService.getTokenCount(text, "")
    }

    override suspend fun call(context: AiCallContext): AiApiResponse {
        val vendor = getVendor()
        val springAiMessages = convertToSpringAiMessages(context.messages)
        val prompt = generatePrompt(context, springAiMessages)

        val maxRetries = 2
        var lastException: Throwable? = null

        for (attempt in 0..maxRetries) {
            val apiKey = apiKeyResolver.resolve(context.applicationId, vendor)
            val chatModel: ChatModel = generateModel(apiKey)

            try {
                val response = chatModel.call(prompt)
                rateLimiter.record(apiKey.id)
                val raw = response.result.output.text ?: "no content"
                val result = postProcessResponse(raw, context)
                recordUsage(response, apiKey, vendor, context, springAiMessages, result)
                return AiApiResponse(vendor, result)
            } catch (cause: Throwable) {
                if (is429Error(cause) && attempt < maxRetries) {
                    log.warn("429 rate limited on keyId: {}, attempt: {}/{}, retrying with another key",
                        apiKey.id, attempt + 1, maxRetries)
                    rateLimiter.markRateLimited(apiKey.id, vendor, apiKey.tier, apiKey.applicationId)
                    lastException = cause
                    continue
                }
                val code = when {
                    is429Error(cause) -> ApiErrorCode.AI_QUOTA_EXCEEDED
                    cause is java.net.SocketTimeoutException ||
                    cause is java.net.ConnectException ||
                    cause is java.io.IOException -> ApiErrorCode.AI_NETWORK_ERROR
                    else -> ApiErrorCode.AI_MODEL_ERROR
                }
                throw AiServiceException(code, cause.message, cause)
            }
        }
        throw AiServiceException(ApiErrorCode.AI_QUOTA_EXCEEDED, "모든 재시도 실패", lastException)
    }

    override suspend fun stream(context: AiCallContext): Flux<String> {
        val vendor = getVendor()
        val springAiMessages = convertToSpringAiMessages(context.messages)
        val prompt = generatePrompt(context, springAiMessages)

        val apiKey = try {
            apiKeyResolver.resolve(context.applicationId, vendor)
        } catch (e: AiServiceException) {
            return Flux.error(e)
        }
        val chatModel: ChatModel = generateModel(apiKey)
        rateLimiter.record(apiKey.id)
        return chatModel.stream(prompt).map { it.result.output.text ?: "" }
    }

    private fun is429Error(cause: Throwable): Boolean {
        val message = cause.message?.lowercase() ?: ""
        return message.contains("429") ||
            message.contains("rate limit") ||
            message.contains("resource_exhausted") ||
            message.contains("too many requests")
    }

    /**
     * 정상 응답 후 ai_usage_logs 에 사용량 기록. 실패해도 호출 결과에 영향 없도록 try-catch.
     * 토큰 수는 ChatResponse.metadata.usage 우선, 없으면 tokenizerService 로 입력만 추정.
     */
    protected open fun recordUsage(
        response: ChatResponse,
        apiKey: ApiKey,
        vendor: Vendor,
        context: AiCallContext,
        springAiMessages: List<Message>,
        responseText: String,
    ) {
        try {
            val usage = response.metadata?.usage
            val promptTokens = usage?.promptTokens?.toInt()
                ?: calculateTokenCountFromMessages(springAiMessages)
            val completionTokens = usage?.completionTokens?.toInt()
                ?: tokenizerService.getTokenCount(responseText, "")

            aiUsageLogsRepository.save(
                AiUsageLogs.create(
                    apiKeyId = apiKey.id,
                    applicationId = apiKey.applicationId,
                    model = context.model ?: "unknown",
                    vendor = vendor,
                    contentType = ContentType.TEXT,
                    promptToken = promptTokens,
                    completionToken = completionTokens,
                    sessionId = context.sessionId,
                ),
            )
        } catch (e: Exception) {
            log.warn("ai_usage_logs save 실패 (vendor={}, keyId={}): {}", vendor, apiKey.id, e.message)
        }
    }
}
