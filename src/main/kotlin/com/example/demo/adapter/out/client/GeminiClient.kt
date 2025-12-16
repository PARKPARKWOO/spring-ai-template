package com.example.demo.adapter.out.client

import com.example.demo.adapter.out.persistence.AiUsageLogsRepository
import com.example.demo.adapter.out.persistence.ClientApiKeyRepository
import com.example.demo.business.QuotaService
import com.example.demo.business.TokenizerService
import com.example.demo.model.Vendor
import com.example.demo.model.api.ApiKey
import com.example.demo.model.api.GoogleApiKey
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatModel
import org.springframework.stereotype.Component

@Component
class GeminiClient(
    private val tokenizerService: TokenizerService,
    private val quotaService: QuotaService,
    private val aiUsageLogsRepository: AiUsageLogsRepository,
    private val clientApiKeyRepository: ClientApiKeyRepository,
    private val vertexAi: VertexAiGeminiChatModel,
): AbstractAiCallTemplate(
    tokenizerService = tokenizerService,
    quotaService = quotaService,
    aiUsageLogsRepository = aiUsageLogsRepository,
    clientApiKeyRepository = clientApiKeyRepository,
) {
    override fun getVendor(): Vendor = Vendor.GOOGLE

    override fun call(prompt: Prompt, apiKey: ApiKey): ChatResponse {
        // ApiKey를 사용하여 동적으로 ChatModel 생성 (현재는 기존 ChatModel 사용)
        // TODO: apiKey.getApiKeyValue()를 사용하여 동적으로 VertexAiGeminiChatModel 생성
        val googleApiKey = apiKey as? GoogleApiKey
            ?: throw IllegalArgumentException("Invalid API key type for Google")
        
        // 현재는 기존 ChatModel 사용, 나중에 동적으로 생성하도록 수정 필요
        return vertexAi.call(prompt)
    }
    override fun generatePrompt(
        userMessage: String,
        clientId: Long
    ): Prompt {
        TODO("Not yet implemented")
    }
}