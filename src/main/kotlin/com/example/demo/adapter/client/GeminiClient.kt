package com.example.demo.adapter.client

import com.example.demo.adapter.persistence.AiUsageLogsRepository
import com.example.demo.business.QuotaService
import com.example.demo.business.TokenizerService
import com.example.demo.model.Vendor
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatModel
import org.springframework.stereotype.Component

@Component
class GeminiClient(
    private val tokenizerService: TokenizerService,
    private val quotaService: QuotaService,
    private val aiUsageLogsRepository: AiUsageLogsRepository,
    private val vertexAi: VertexAiGeminiChatModel,
): AbstractAiCallTemplate(
    tokenizerService = tokenizerService,
    quotaService = quotaService,
    aiUsageLogsRepository = aiUsageLogsRepository,
) {
    override fun getVendor(): Vendor = Vendor.GOOGLE

    override fun call(prompt: Prompt): ChatResponse = vertexAi.call(prompt)
}