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
import com.example.demo.model.api.ApiKey
import com.example.demo.model.api.GoogleApiKey
import com.google.genai.Client
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.content.Media
import org.springframework.ai.google.genai.GoogleGenAiChatModel
import org.springframework.ai.google.genai.GoogleGenAiChatOptions
import org.springframework.core.io.ByteArrayResource
import org.springframework.stereotype.Component
import org.springframework.util.MimeType

/**
 * Gemini Vision(멀티모달 이미지 입력) 호출 어댑터.
 *
 * 기존 [GeminiClient]의 chat 호출과 별개의 경로이지만,
 * API Key 해결 / Rate Limit 재시도 패턴은 동일하게 따른다.
 *
 * Gemini 는 [GoogleGenAiChatModel]이 Spring AI 의 [Media] 를 네이티브로 지원하므로
 * 마지막 user 메시지에 이미지들을 attach 해서 Prompt 를 구성한다.
 */
@Component
class GeminiVisionClient(
    private val apiKeyResolver: ApiKeyResolver,
    private val rateLimiter: ApiKeyRateLimiter,
    private val imageResolver: ImageResolver,
    private val tokenizerService: TokenizerService,
    private val aiUsageLogsRepository: AiUsageLogsRepository,
) : VisionCallPort {

    private val log = LoggerFactory.getLogger(GeminiVisionClient::class.java)

    override fun getVendor(): Vendor = Vendor.GOOGLE

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
            val apiKey = apiKeyResolver.resolve(context.applicationId, Vendor.GOOGLE)
            val chatModel = buildChatModel(apiKey)
            val prompt = Prompt(
                springAiMessages,
                GoogleGenAiChatOptions.builder()
                    .maxOutputTokens(context.effectiveMaxTokens())
                    .build(),
            )

            try {
                val response = chatModel.call(prompt)
                rateLimiter.record(apiKey.id)
                val result = response.result.output.text ?: "no content"
                recordUsage(response, apiKey, context, springAiMessages, result)
                return VisionResponse(Vendor.GOOGLE, result)
            } catch (cause: Throwable) {
                if (is429Error(cause) && attempt < maxRetries) {
                    val cooldownMs = RetryAfterParser.parse(cause.message)
                        ?: ApiKeyRateLimiter.DEFAULT_COOLDOWN_MS
                    log.warn(
                        "429 rate limited (vision) keyId={} attempt={}/{} cooldown={}ms",
                        apiKey.id, attempt + 1, maxRetries, cooldownMs,
                    )
                    rateLimiter.markRateLimited(
                        apiKey.id, Vendor.GOOGLE, apiKey.tier, apiKey.applicationId, cooldownMs,
                    )
                    lastException = cause
                    continue
                }
                log.error("Gemini vision call failed keyId={} err={}", apiKey.id, cause.message)
                throw AiServiceException(ApiErrorCode.AI_MODEL_ERROR, cause.message ?: "vision call failed", cause)
            }
        }

        throw AiServiceException(
            ApiErrorCode.AI_MODEL_ERROR,
            "Vision 호출이 ${maxRetries + 1}회 모두 실패했습니다",
            lastException,
        )
    }

    private fun buildChatModel(apiKey: ApiKey): GoogleGenAiChatModel {
        val googleApiKey = apiKey as? GoogleApiKey
            ?: throw AiServiceException(
                ApiErrorCode.AI_API_KEY_NOT_FOUND,
                "Google 벤더에 대한 유효한 API 키 타입이 아닙니다. (keyId=${apiKey.id})",
            )
        val client = Client.builder().apiKey(googleApiKey.apiKey).build()
        return GoogleGenAiChatModel.builder().genAiClient(client).build()
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
            // user 메시지가 없으면 기본 프롬프트와 함께 이미지 첨부
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
        return msg.contains("429") || msg.contains("RESOURCE_EXHAUSTED", ignoreCase = true)
    }

    private fun recordUsage(
        response: org.springframework.ai.chat.model.ChatResponse,
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
                    vendor = Vendor.GOOGLE,
                    contentType = ContentType.IMAGE,
                    promptToken = promptTokens,
                    completionToken = completionTokens,
                    sessionId = context.sessionId,
                ),
            )
        } catch (e: Exception) {
            log.warn("vision ai_usage_logs save 실패 (keyId={}): {}", apiKey.id, e.message)
        }
    }
}
