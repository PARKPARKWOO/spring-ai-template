package com.example.demo.adapter.out.client

import com.example.demo.adapter.out.persistence.ApiKeyRepository
import com.example.demo.business.TokenizerService
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
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.stereotype.Component

@Component
class AnthropicClient(
    tokenizerService: TokenizerService,
    apiKeyRepository: ApiKeyRepository,
) : AbstractAiCallTemplate(tokenizerService, apiKeyRepository) {

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

        // Anthropic 벤더 옵션 추출
        val anthropicOptions = context.vendorOptions as? VendorOptions.AnthropicOptions

        // Anthropic 캐시 옵션 설정
        val cacheStrategy = anthropicOptions?.cacheStrategy
        val cacheTtl = anthropicOptions?.cacheTtl

        if (cacheStrategy != null || cacheTtl != null) {
            val cacheOptionsBuilder = AnthropicCacheOptions.builder()

            cacheStrategy?.let { strategyStr ->
                try {
                    val strategy = AnthropicCacheStrategy.valueOf(strategyStr)
                    cacheOptionsBuilder.strategy(strategy)
                } catch (e: IllegalArgumentException) {
                    log.warn(
                        "Invalid cache strategy: $strategyStr. Valid values: ${AnthropicCacheStrategy.values().joinToString { it.name }}",
                    )
                }
            }

            cacheTtl?.let { ttlStr ->
                try {
                    val ttl = AnthropicCacheTtl.valueOf(ttlStr)
                    log.info("Cache TTL requested: $ttlStr, but messageTypeTtl requires MessageType enum")
                } catch (e: IllegalArgumentException) {
                    log.warn("Invalid cache TTL: $ttlStr. Valid values: ${AnthropicCacheTtl.values().joinToString { it.name }}")
                }
            }

            optionsBuilder.cacheOptions(cacheOptionsBuilder.build())
        }

        return Prompt(messages, optionsBuilder.build())
    }
}
