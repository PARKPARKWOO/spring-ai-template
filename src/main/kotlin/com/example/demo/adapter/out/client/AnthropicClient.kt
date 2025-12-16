package com.example.demo.adapter.out.client

import com.example.demo.adapter.out.persistence.AiUsageLogsRepository
import com.example.demo.adapter.out.persistence.ClientApiKeyRepository
import com.example.demo.business.QuotaService
import com.example.demo.business.TokenizerService
import com.example.demo.model.Vendor
import com.example.demo.model.api.AnthropicApiKey
import com.example.demo.model.api.ApiKey
import org.springframework.ai.anthropic.AnthropicChatModel
import org.springframework.ai.anthropic.AnthropicChatOptions
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.stereotype.Component

@Component
class AnthropicClient(
    private val tokenizerService: TokenizerService,
    private val quotaService: QuotaService,
    private val aiUsageLogsRepository: AiUsageLogsRepository,
    private val clientApiKeyRepository: ClientApiKeyRepository,
    private val anthropicChatModel: AnthropicChatModel,
): AbstractAiCallTemplate(
    tokenizerService = tokenizerService,
    quotaService = quotaService,
    aiUsageLogsRepository = aiUsageLogsRepository,
    clientApiKeyRepository = clientApiKeyRepository,
) {
    override fun getVendor(): Vendor = Vendor.ANTHROPIC

    override fun call(prompt: Prompt, apiKey: ApiKey): ChatResponse {
        // ApiKey를 사용하여 동적으로 ChatModel 생성 (현재는 기존 ChatModel 사용)
        // TODO: apiKey.getApiKeyValue()를 사용하여 동적으로 AnthropicApi와 ChatModel 생성
        val anthropicApiKey = apiKey as? AnthropicApiKey
            ?: throw IllegalArgumentException("Invalid API key type for Anthropic")
        
        // 현재는 기존 ChatModel 사용, 나중에 동적으로 생성하도록 수정 필요
        return anthropicChatModel.call(prompt)
    }

    override fun generatePrompt(
        userMessage: String,
        clientId: Long
    ): Prompt {
//        val options = AnthropicChatOptions.builder()
//            .build()
//        return Prompt.builder()
//            .messages()
//            .chatOptions(options)
//            .build()
        return Prompt(userMessage)
    }
}