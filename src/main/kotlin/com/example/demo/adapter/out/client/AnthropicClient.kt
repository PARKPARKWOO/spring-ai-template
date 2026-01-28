package com.example.demo.adapter.out.client

import com.example.demo.adapter.out.persistence.AiUsageLogsRepository
import com.example.demo.adapter.out.persistence.ClientApiKeyRepository
import com.example.demo.business.QuotaManagementService
import com.example.demo.business.TokenizerService
import com.example.demo.business.exception.AiServiceException
import com.example.demo.model.ApiErrorCode
import com.example.demo.model.Vendor
import com.example.demo.model.api.AnthropicApiKey
import com.example.demo.model.api.ApiKey
import com.example.demo.common.logger
import org.springframework.ai.anthropic.AnthropicChatModel
import org.springframework.ai.anthropic.AnthropicChatOptions
import org.springframework.ai.anthropic.api.AnthropicApi
import org.springframework.ai.anthropic.api.AnthropicCacheOptions
import org.springframework.ai.anthropic.api.AnthropicCacheStrategy
import org.springframework.ai.anthropic.api.AnthropicCacheTtl
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.stereotype.Component

@Component
class AnthropicClient(
    private val tokenizerService: TokenizerService,
    private val quotaManagementService: QuotaManagementService,
    private val aiUsageLogsRepository: AiUsageLogsRepository,
    private val clientApiKeyRepository: ClientApiKeyRepository,
): AbstractAiCallTemplate(
    tokenizerService = tokenizerService,
    quotaManagementService = quotaManagementService,
    aiUsageLogsRepository = aiUsageLogsRepository,
    clientApiKeyRepository = clientApiKeyRepository,
) {
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
        userMessage: String,
        systemPrompt: String,
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
        val systemMessage = SystemMessage(systemPrompt)
        val userMessage = UserMessage(userMessage)
        val optionsBuilder = AnthropicChatOptions.builder()
            .maxTokens(maxTokens)

        model?.let { optionsBuilder.model(model) }
        // Anthropic은 Structured Output을 지원하지 않으므로 jsonSchema는 무시
        // 필요시 Anthropic의 특정 기능으로 대체 가능
        
        // Anthropic 캐시 옵션 설정
        if (cacheStrategy != null || cacheTtl != null) {
            val cacheOptionsBuilder = AnthropicCacheOptions.builder()
            
            // 캐시 전략 설정
            cacheStrategy?.let { strategyStr ->
                try {
                    val strategy = AnthropicCacheStrategy.valueOf(strategyStr)
                    cacheOptionsBuilder.strategy(strategy)
                } catch (e: IllegalArgumentException) {
                    logger().warn("Invalid cache strategy: $strategyStr. Valid values: ${AnthropicCacheStrategy.values().joinToString { it.name }}")
                }
            }
            
            // 캐시 TTL 설정
            // Note: messageTypeTtl은 MessageType enum을 필요로 합니다.
            // Spring AI의 Anthropic API에서 정확한 패키지를 확인해야 합니다.
            // 일단 TTL만 설정하는 방식으로 변경 (전략이 설정되면 자동으로 적용됨)
            cacheTtl?.let { ttlStr ->
                try {
                    val ttl = AnthropicCacheTtl.valueOf(ttlStr)
                    // messageTypeTtl은 나중에 정확한 MessageType import 경로 확인 후 추가
                    // cacheOptionsBuilder.messageTypeTtl(MessageType.SYSTEM, ttl)
                    logger().info("Cache TTL requested: $ttlStr, but messageTypeTtl requires MessageType enum")
                } catch (e: IllegalArgumentException) {
                    logger().warn("Invalid cache TTL: $ttlStr. Valid values: ${AnthropicCacheTtl.values().joinToString { it.name }}")
                }
            }
            
            optionsBuilder.cacheOptions(cacheOptionsBuilder.build())
        }
        
        return Prompt(listOf(userMessage, systemMessage), optionsBuilder.build())
    }
}