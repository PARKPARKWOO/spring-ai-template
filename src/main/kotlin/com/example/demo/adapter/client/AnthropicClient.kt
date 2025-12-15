package com.example.demo.adapter.client

import com.example.demo.adapter.persistence.AiUsageLogsRepository
import com.example.demo.business.QuotaService
import com.example.demo.business.TokenizerService
import com.example.demo.model.Vendor
import org.springframework.ai.anthropic.AnthropicChatModel
import org.springframework.ai.anthropic.AnthropicChatOptions
import org.springframework.ai.anthropic.api.AnthropicApi
import org.springframework.ai.chat.messages.AbstractMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.stereotype.Component

@Component
class AnthropicClient(
    private val tokenizerService: TokenizerService,
    private val quotaService: QuotaService,
    private val aiUsageLogsRepository: AiUsageLogsRepository,
    private val anthropicChatModel: AnthropicChatModel,
): AbstractAiCallTemplate(
    tokenizerService = tokenizerService,
    quotaService = quotaService,
    aiUsageLogsRepository = aiUsageLogsRepository,
) {
    override fun getVendor(): Vendor = Vendor.ANTHROPIC

    override fun call(prompt: Prompt): ChatResponse {
        return anthropicChatModel.call(prompt)
    }

    override fun generatePrompt(
        userMessage: String,
        clientId: Long
    ): Prompt {
        val options = AnthropicChatOptions.builder()
            .build()

        return Prompt.builder()
            .messages()
            .chatOptions(options)
            .build()
    }

}