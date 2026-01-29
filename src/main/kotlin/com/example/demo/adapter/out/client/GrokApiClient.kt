package com.example.demo.adapter.out.client

import com.example.demo.adapter.out.persistence.AiUsageLogsRepository
import com.example.demo.adapter.out.persistence.ClientApiKeyRepository
import com.example.demo.business.QuotaManagementService
import com.example.demo.business.TokenizerService
import com.example.demo.business.exception.AiServiceException
import com.example.demo.model.ApiErrorCode
import com.example.demo.model.Vendor
import com.example.demo.model.api.ApiKey
import com.example.demo.model.api.XAiApiKey
import org.springframework.ai.anthropic.AnthropicChatOptions
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.ai.openai.api.OpenAiApi
import org.springframework.ai.openai.api.ResponseFormat
import org.springframework.stereotype.Component

@Component
class GrokApiClient(
    private val tokenizerService: TokenizerService,
    private val quotaManagementService: QuotaManagementService,
    private val aiUsageLogsRepository: AiUsageLogsRepository,
    private val clientApiKeyRepository: ClientApiKeyRepository,
) : AbstractAiCallTemplate(
    tokenizerService = tokenizerService,
    quotaManagementService = quotaManagementService,
    aiUsageLogsRepository = aiUsageLogsRepository,
    clientApiKeyRepository = clientApiKeyRepository,
) {
    override fun getVendor(): Vendor = Vendor.X_AI

    override fun generateModel(apiKey: ApiKey): ChatModel {
        val xAiApiKey = apiKey as? XAiApiKey
            ?: throw AiServiceException(
                ApiErrorCode.AI_API_KEY_NOT_FOUND,
                "xAI/Grok 벤더에 대한 유효한 API 키 타입이 아닙니다. (API Key ID: ${apiKey.id})"
            )

        // xAI Grok은 OpenAI API와 호환되므로 OpenAiApi 사용
        // base URL을 xAI 엔드포인트로 설정
        val api = OpenAiApi.builder()
            .apiKey(xAiApiKey.apiKey)
            .baseUrl("https://api.x.ai") // xAI Grok API 엔드포인트
            .completionsPath("/v1/chat/completions")
            .build()

        return OpenAiChatModel.builder()
            .openAiApi(api)
            .defaultOptions(
                org.springframework.ai.openai.OpenAiChatOptions.builder()
                    .model("grok-4")
                    .build()
            )
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
        val optionsBuilder = OpenAiChatOptions.builder()
            .maxTokens(maxTokens)

        model?.let { optionsBuilder.model(model) }

        // JSON Schema가 제공되면 Structured Output 설정 (OpenAI API 호환)
        if (jsonSchema != null && jsonSchema.isNotBlank()) {
            optionsBuilder.responseFormat(
                ResponseFormat(ResponseFormat.Type.JSON_SCHEMA, jsonSchema)
            )
        }
        
        return Prompt(messages, optionsBuilder.build())
    }
}