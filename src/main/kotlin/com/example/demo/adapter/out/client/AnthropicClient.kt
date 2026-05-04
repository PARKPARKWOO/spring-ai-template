package com.example.demo.adapter.out.client

import com.example.demo.business.TokenizerService
import com.example.demo.common.ratelimit.ApiKeyRateLimiter
import com.example.demo.business.exception.AiServiceException
import com.example.demo.dto.AiCallContext
import com.example.demo.dto.VendorOptions
import org.slf4j.LoggerFactory
import com.example.demo.model.ApiErrorCode
import com.example.demo.model.Vendor
import com.example.demo.model.api.AnthropicApiKey
import com.example.demo.model.api.ApiKey
import org.springframework.ai.anthropic.AnthropicChatModel
import org.springframework.ai.anthropic.AnthropicChatOptions
import org.springframework.ai.anthropic.api.AnthropicApi
import org.springframework.ai.anthropic.api.AnthropicCacheOptions
import org.springframework.ai.anthropic.api.AnthropicCacheStrategy
import org.springframework.ai.anthropic.api.AnthropicCacheTtl
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.stereotype.Component

@Component
class AnthropicClient(
    tokenizerService: TokenizerService,
    apiKeyResolver: ApiKeyResolver,
    rateLimiter: ApiKeyRateLimiter,
) : AbstractAiCallTemplate(tokenizerService, apiKeyResolver, rateLimiter) {

    private val log = LoggerFactory.getLogger(AnthropicClient::class.java)

    override fun getVendor(): Vendor = Vendor.ANTHROPIC

    override fun generateModel(apiKey: ApiKey): ChatModel {
        val anthropicApiKey =
            apiKey as? AnthropicApiKey
                ?: throw AiServiceException(
                    ApiErrorCode.AI_API_KEY_NOT_FOUND,
                    "Anthropic 벤더에 대한 유효한 API 키 타입이 아닙니다. (API Key ID: ${apiKey.id})",
                )
        val api =
            AnthropicApi
                .builder()
                .apiKey(anthropicApiKey.apiKey)
                .build()
        return AnthropicChatModel
            .builder()
            .anthropicApi(api)
            .build()
    }

    override fun generatePrompt(context: AiCallContext, messages: List<Message>): Prompt {
        val optionsBuilder =
            AnthropicChatOptions
                .builder()
                .maxTokens(context.effectiveMaxTokens())

        context.model?.let { optionsBuilder.model(it) }

        val anthropicOptions = context.vendorOptions as? VendorOptions.AnthropicOptions

        // Tool Calling — Spring AI Anthropic 도 toolNames 로 등록된 function bean 호출 가능
        anthropicOptions?.toolNames
            ?.takeIf { it.isNotEmpty() }
            ?.let { optionsBuilder.toolNames(it.toSet()) }

        // Anthropic 캐시 옵션 (strategy + ttl 모두 builder 에 직접 전달)
        val cacheStrategy = anthropicOptions?.cacheStrategy
        val cacheTtl = anthropicOptions?.cacheTtl
        if (cacheStrategy != null || cacheTtl != null) {
            val cacheOptionsBuilder = AnthropicCacheOptions.builder()

            cacheStrategy?.let { strategyStr ->
                runCatching { AnthropicCacheStrategy.valueOf(strategyStr) }
                    .onSuccess { cacheOptionsBuilder.strategy(it) }
                    .onFailure {
                        log.warn(
                            "Invalid cache strategy: {}. Valid: {}",
                            strategyStr,
                            AnthropicCacheStrategy.values().joinToString { it.name },
                        )
                    }
            }

            cacheTtl?.let { ttlStr ->
                runCatching { AnthropicCacheTtl.valueOf(ttlStr) }
                    .onSuccess { ttl ->
                        // Spring AI 1.1.3 의 AnthropicCacheOptions 는 messageTypeTtl(MessageType -> Ttl) 매핑.
                        // 모든 메시지 타입에 동일 TTL 적용 (system / user / assistant 공통).
                        val map = org.springframework.ai.chat.messages.MessageType.values()
                            .associateWith { ttl }
                        cacheOptionsBuilder.messageTypeTtl(map)
                    }
                    .onFailure {
                        log.warn(
                            "Invalid cache TTL: {}. Valid: {}",
                            ttlStr,
                            AnthropicCacheTtl.values().joinToString { it.name },
                        )
                    }
            }

            optionsBuilder.cacheOptions(cacheOptionsBuilder.build())
        }

        // jsonSchema 가 있으면 system prompt 로 JSON 강제 (Spring AI Anthropic 에 native structured output 미지원)
        val finalMessages =
            context.jsonSchema
                ?.takeIf { it.isNotBlank() }
                ?.let { schema -> prependJsonSchemaInstruction(messages, schema) }
                ?: messages

        return Prompt(finalMessages, optionsBuilder.build())
    }

    /**
     * 응답 후처리. Anthropic 은 ```json fence + 짧은 prefix 를 동반할 수 있어
     * fence 로 감싸진 JSON 본문만 추출. fence 가 없으면 원문 그대로 반환.
     * jsonSchema 가 요청된 케이스에서만 동작하여, 일반 텍스트 응답은 영향 없음.
     */
    override fun postProcessResponse(text: String, context: AiCallContext): String {
        if (context.jsonSchema.isNullOrBlank()) return text
        return stripJsonFence(text)
    }

    private fun prependJsonSchemaInstruction(messages: List<Message>, schema: String): List<Message> {
        val instruction =
            "You MUST respond with ONLY a single valid JSON object that strictly matches this JSON Schema:\n" +
                "$schema\n" +
                "Do not include any explanation, prose, or markdown code fences. Return raw JSON only."
        return listOf(SystemMessage(instruction)) + messages
    }

    private fun stripJsonFence(raw: String): String {
        val trimmed = raw.trim()
        // ```json ... ``` 또는 ``` ... ``` 형태
        val fenceRegex = Regex("^```(?:json)?\\s*([\\s\\S]*?)\\s*```\\s*$", RegexOption.IGNORE_CASE)
        fenceRegex.find(trimmed)?.let { return it.groupValues[1].trim() }
        // 앞뒤로 prose 가 붙고 그 안에 JSON 객체가 있는 경우 — 첫 { 부터 마지막 } 까지 추출
        val firstBrace = trimmed.indexOf('{')
        val lastBrace = trimmed.lastIndexOf('}')
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1)
        }
        return trimmed
    }
}
