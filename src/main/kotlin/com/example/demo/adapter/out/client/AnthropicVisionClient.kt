package com.example.demo.adapter.out.client

import com.example.demo.adapter.out.persistence.AiUsageLogsRepository
import com.example.demo.business.TokenizerService
import com.example.demo.business.exception.AiServiceException
import com.example.demo.common.ratelimit.ApiKeyRateLimiter
import com.example.demo.common.ratelimit.RetryAfterParser
import com.example.demo.dto.AiCallContext
import com.example.demo.dto.AssistantChatMessage
import com.example.demo.dto.ChatMessage
import com.example.demo.dto.ImageSource
import com.example.demo.dto.SystemChatMessage
import com.example.demo.dto.UserChatMessage
import com.example.demo.dto.VisionResponse
import com.example.demo.model.AiUsageLogs
import com.example.demo.model.ApiErrorCode
import com.example.demo.model.ContentType
import com.example.demo.model.Vendor
import com.example.demo.model.api.AnthropicApiKey
import com.example.demo.model.api.ApiKey
import org.slf4j.LoggerFactory
import org.springframework.ai.anthropic.AnthropicChatModel
import org.springframework.ai.anthropic.AnthropicChatOptions
import org.springframework.ai.anthropic.api.AnthropicApi
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.content.Media
import org.springframework.core.io.ByteArrayResource
import org.springframework.stereotype.Component
import org.springframework.util.MimeType

/**
 * Anthropic Claude Vision(멀티모달 이미지 입력) 호출 어댑터.
 *
 * Spring AI 의 [AnthropicChatModel] 은 표준 [Media] 인터페이스를 통해 이미지 input 을
 * Anthropic API 의 image content block 으로 자동 매핑한다. 따라서 [GeminiVisionClient]
 * 와 동일한 패턴으로 multimodal Prompt 를 구성할 수 있다.
 *
 * 모델 예: `claude-sonnet-4-6`, `claude-3-5-sonnet-20241022` (vision 지원 모델만 사용 가능).
 *
 * ContentType=IMAGE 로 ai_usage_logs 기록.
 */
@Component
class AnthropicVisionClient(
    private val apiKeyResolver: ApiKeyResolver,
    private val rateLimiter: ApiKeyRateLimiter,
    private val imageResolver: ImageResolver,
    private val tokenizerService: TokenizerService,
    private val aiUsageLogsRepository: AiUsageLogsRepository,
) : VisionCallPort {

    private val log = LoggerFactory.getLogger(AnthropicVisionClient::class.java)

    override fun getVendor(): Vendor = Vendor.ANTHROPIC

    override suspend fun call(context: AiCallContext, images: List<ImageSource>): VisionResponse {
        if (images.isEmpty()) {
            throw AiServiceException(
                ApiErrorCode.AI_REQUEST_VALIDATION_FAILED,
                "Vision 요청에는 이미지가 하나 이상 필요합니다",
            )
        }

        val resolvedImages = images.map { imageResolver.resolve(it) }
        val springAiMessages = buildMessagesWithImages(context.messages, resolvedImages)

        val maxRetries = 2
        var lastException: Throwable? = null

        for (attempt in 0..maxRetries) {
            val apiKey = apiKeyResolver.resolve(context.applicationId, Vendor.ANTHROPIC)
            val chatModel = buildChatModel(apiKey)
            val optionsBuilder = AnthropicChatOptions
                .builder()
                .maxTokens(context.effectiveMaxTokens())
            context.model?.let { optionsBuilder.model(it) }
            val prompt = Prompt(springAiMessages, optionsBuilder.build())

            try {
                val response = chatModel.call(prompt)
                rateLimiter.record(apiKey.id)
                val result = response.result.output.text ?: "no content"
                recordUsage(response, apiKey, context, springAiMessages, result)
                return VisionResponse(Vendor.ANTHROPIC, result)
            } catch (cause: Throwable) {
                if (is429Error(cause) && attempt < maxRetries) {
                    val cooldownMs = RetryAfterParser.parse(cause.message)
                        ?: ApiKeyRateLimiter.DEFAULT_COOLDOWN_MS
                    log.warn(
                        "429 rate limited (anthropic vision) keyId={} attempt={}/{} cooldown={}ms",
                        apiKey.id, attempt + 1, maxRetries, cooldownMs,
                    )
                    rateLimiter.markRateLimited(
                        apiKey.id, Vendor.ANTHROPIC, apiKey.tier, apiKey.applicationId, cooldownMs,
                    )
                    lastException = cause
                    continue
                }
                log.error("Anthropic vision call failed keyId={} err={}", apiKey.id, cause.message)
                throw AiServiceException(ApiErrorCode.AI_MODEL_ERROR, cause.message ?: "vision call failed", cause)
            }
        }

        throw AiServiceException(
            ApiErrorCode.AI_MODEL_ERROR,
            "Anthropic vision 호출이 ${maxRetries + 1}회 모두 실패했습니다",
            lastException,
        )
    }

    private fun buildChatModel(apiKey: ApiKey): AnthropicChatModel {
        val anthropicApiKey = apiKey as? AnthropicApiKey
            ?: throw AiServiceException(
                ApiErrorCode.AI_API_KEY_NOT_FOUND,
                "Anthropic 벤더에 대한 유효한 API 키 타입이 아닙니다. (keyId=${apiKey.id})",
            )
        val api = AnthropicApi.builder().apiKey(anthropicApiKey.apiKey).build()
        return AnthropicChatModel.builder().anthropicApi(api).build()
    }

    private fun buildMessagesWithImages(
        chatMessages: List<ChatMessage>,
        resolvedImages: List<ImageResolver.ResolvedImage>,
    ): List<Message> {
        val base = chatMessages.map { msg ->
            when (msg) {
                is UserChatMessage -> UserMessage(msg.content)
                is SystemChatMessage -> SystemMessage(msg.content)
                is AssistantChatMessage -> AssistantMessage(msg.content)
            }
        }.toMutableList()

        val mediaList = resolvedImages.map { img ->
            Media(MimeType.valueOf(img.mimeType), ByteArrayResource(img.bytes))
        }

        val lastUserIdx = base.indexOfLast { it is UserMessage }
        if (lastUserIdx >= 0) {
            val last = base[lastUserIdx] as UserMessage
            base[lastUserIdx] = UserMessage.builder()
                .text(last.text)
                .media(mediaList)
                .build()
        } else {
            base.add(
                UserMessage.builder()
                    .text("이 이미지들을 설명해주세요.")
                    .media(mediaList)
                    .build(),
            )
        }
        return base
    }

    private fun is429Error(cause: Throwable): Boolean {
        val msg = cause.message ?: return false
        return msg.contains("429") ||
            msg.contains("rate_limit", ignoreCase = true) ||
            msg.contains("rate limit", ignoreCase = true) ||
            msg.contains("too many requests", ignoreCase = true)
    }

    private fun recordUsage(
        response: ChatResponse,
        apiKey: ApiKey,
        context: AiCallContext,
        springAiMessages: List<Message>,
        responseText: String,
    ) {
        try {
            val usage = response.metadata?.usage
            val promptTokens = usage?.promptTokens?.toInt()
                ?: tokenizerService.getTokenCount(springAiMessages.joinToString(" ") { it.text }, "")
            val completionTokens = usage?.completionTokens?.toInt()
                ?: tokenizerService.getTokenCount(responseText, "")

            aiUsageLogsRepository.save(
                AiUsageLogs.create(
                    apiKeyId = apiKey.id,
                    applicationId = apiKey.applicationId,
                    model = context.model ?: "unknown",
                    vendor = Vendor.ANTHROPIC,
                    contentType = ContentType.IMAGE,
                    promptToken = promptTokens,
                    completionToken = completionTokens,
                    sessionId = context.sessionId,
                ),
            )
        } catch (e: Exception) {
            log.warn("anthropic vision ai_usage_logs save 실패 (keyId={}): {}", apiKey.id, e.message)
        }
    }
}
