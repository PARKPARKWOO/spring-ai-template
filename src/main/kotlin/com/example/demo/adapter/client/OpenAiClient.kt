package com.example.demo.adapter.client

import com.example.demo.adapter.persistence.AiUsageLogsRepository
import com.example.demo.business.QuotaService
import com.example.demo.business.TokenizerService
import com.example.demo.model.Vendor
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.ai.openai.api.OpenAiApi
import org.springframework.stereotype.Component

@Component
class OpenAiClient(
    private val quotaService: QuotaService,
    private val tokenizerService: TokenizerService,
    private val aiUsageLogsRepository: AiUsageLogsRepository,
    private val openAiApi: OpenAiApi,
    private val openAiChatModel: OpenAiChatModel,
): AbstractAiCallTemplate(
    tokenizerService = tokenizerService,
    quotaService = quotaService,
    aiUsageLogsRepository = aiUsageLogsRepository,
) {
    override fun getVendor(): Vendor = Vendor.OPENAI

    override fun call(prompt: Prompt): ChatResponse {
        return openAiChatModel.call(prompt)
    }
}