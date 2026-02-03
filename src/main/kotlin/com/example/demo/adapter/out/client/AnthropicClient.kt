package com.example.demo.adapter.out.client

import com.example.demo.adapter.out.persistence.ApiKeyRepository
import com.example.demo.business.TokenizerService
import com.example.demo.business.exception.AiServiceException
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
    override fun getVendor(): Vendor = Vendor.ANTHROPIC
    override fun generateModel(apiKey: ApiKey): ChatModel {
        val anthropicApiKey = apiKey as? AnthropicApiKey
            ?: throw AiServiceException(
                ApiErrorCode.AI_API_KEY_NOT_FOUND,
                "Anthropic 벤더에 대한 유효한 API 키 타입이 아닙니다. (API Key ID: ${apiKey.id})"
            )
        val api = AnthropicApi.builder()
            .apiKey(anthropicApiKey.apiKey)
            .build()
        return AnthropicChatModel.builder()
            .anthropicApi(api)
            .build()
    }

    override fun generatePrompt(
        messages: List<Message>,
        maxTokens: Int,
        jsonSchema: String?,
        urlContexts: List<String>?,
        enableGoogleSearch: Boolean?,
        toolNames: List<String>?,
        model: String?,
        useCachedContent: Boolean?,
        cachedContentName: String?,
        cacheStrategy: String?,
        cacheTtl: String?,
    ): Prompt {
        val optionsBuilder = AnthropicChatOptions.builder()
            .maxTokens(maxTokens)

        model?.let { optionsBuilder.model(model) }
        
        // Anthropic 캐시 옵션 설정
        if (cacheStrategy != null || cacheTtl != null) {
            val cacheOptionsBuilder = AnthropicCacheOptions.builder()
            
            cacheStrategy?.let { strategyStr ->
                try {
                    val strategy = AnthropicCacheStrategy.valueOf(strategyStr)
                    cacheOptionsBuilder.strategy(strategy)
                } catch (e: IllegalArgumentException) {
                    logger().warn("Invalid cache strategy: $strategyStr. Valid values: ${AnthropicCacheStrategy.values().joinToString { it.name }}")
                }
            }
            
            cacheTtl?.let { ttlStr ->
                try {
                    val ttl = AnthropicCacheTtl.valueOf(ttlStr)
                    logger().info("Cache TTL requested: $ttlStr, but messageTypeTtl requires MessageType enum")
                } catch (e: IllegalArgumentException) {
                    logger().warn("Invalid cache TTL: $ttlStr. Valid values: ${AnthropicCacheTtl.values().joinToString { it.name }}")
                }
            }
            
            optionsBuilder.cacheOptions(cacheOptionsBuilder.build())
        }
        
        return Prompt(messages, optionsBuilder.build())
    }
}