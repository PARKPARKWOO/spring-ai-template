package com.example.demo.adapter.out.client

import com.example.demo.adapter.out.persistence.AiUsageLogsRepository
import com.example.demo.adapter.out.persistence.ClientApiKeyRepository
import com.example.demo.business.QuotaManagementService
import com.example.demo.business.TokenizerService
import com.example.demo.business.exception.AiServiceException
import com.example.demo.model.ApiErrorCode
import com.example.demo.model.Vendor
import com.example.demo.model.api.ApiKey
import com.example.demo.model.api.OpenAiApiKey
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.ai.openai.api.OpenAiApi
import org.springframework.ai.openai.api.ResponseFormat
import org.springframework.stereotype.Component

@Component
class OpenAiClient(
    private val quotaManagementService: QuotaManagementService,
    private val tokenizerService: TokenizerService,
    private val aiUsageLogsRepository: AiUsageLogsRepository,
    private val clientApiKeyRepository: ClientApiKeyRepository,
) : AbstractAiCallTemplate(
    tokenizerService = tokenizerService,
    quotaManagementService = quotaManagementService,
    aiUsageLogsRepository = aiUsageLogsRepository,
    clientApiKeyRepository = clientApiKeyRepository,
) {
    override fun getVendor(): Vendor = Vendor.OPENAI
    override fun generateModel(apiKey: ApiKey): ChatModel {
        val openAiApiKey = apiKey as? OpenAiApiKey
            ?: throw AiServiceException(
                ApiErrorCode.AI_API_KEY_NOT_FOUND,
                "OpenAI 벤더에 대한 유효한 API 키 타입이 아닙니다. (API Key ID: ${apiKey.id})"
            )

        // 현재는 기존 ChatModel 사용, 나중에 동적으로 생성하도록 수정 필요
        val api = OpenAiApi.builder()
            .apiKey(openAiApiKey.apiKey)
            .build()
        return OpenAiChatModel.builder()
            .openAiApi(api)
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

        // JSON Schema가 제공되면 Structured Output 설정
        if (jsonSchema != null && jsonSchema.isNotBlank()) {
            optionsBuilder.responseFormat(
                ResponseFormat(ResponseFormat.Type.JSON_SCHEMA, jsonSchema)
            )
        }

        return Prompt(messages, optionsBuilder.build())
    }
}