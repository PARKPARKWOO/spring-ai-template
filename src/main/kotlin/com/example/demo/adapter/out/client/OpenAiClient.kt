package com.example.demo.adapter.out.client

import com.example.demo.adapter.out.persistence.ApiKeyRepository
import com.example.demo.business.TokenizerService
import com.example.demo.business.exception.AiServiceException
import com.example.demo.dto.AiCallContext
import com.example.demo.model.ApiErrorCode
import com.example.demo.model.Vendor
import com.example.demo.model.api.ApiKey
import com.example.demo.model.api.OpenAiApiKey
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.ai.openai.api.OpenAiApi
import org.springframework.ai.openai.api.ResponseFormat
import org.springframework.stereotype.Component

@Component
class OpenAiClient(
    tokenizerService: TokenizerService,
    apiKeyRepository: ApiKeyRepository,
) : AbstractAiCallTemplate(tokenizerService, apiKeyRepository) {
    override fun getVendor(): Vendor = Vendor.OPENAI
    override fun generateModel(apiKey: ApiKey): ChatModel {
        val openAiApiKey = apiKey as? OpenAiApiKey
            ?: throw AiServiceException(
                ApiErrorCode.AI_API_KEY_NOT_FOUND,
                "OpenAI 벤더에 대한 유효한 API 키 타입이 아닙니다. (API Key ID: ${apiKey.id})"
            )

        val api = OpenAiApi.builder()
            .apiKey(openAiApiKey.apiKey)
            .build()
        return OpenAiChatModel.builder()
            .openAiApi(api)
            .build()
    }

    override fun generatePrompt(context: AiCallContext, messages: List<Message>): Prompt {
        val optionsBuilder = OpenAiChatOptions.builder()
            .maxTokens(context.effectiveMaxTokens())

        context.model?.let { optionsBuilder.model(it) }

        // JSON Schema가 제공되면 Structured Output 설정
        val jsonSchema = context.jsonSchema
        if (jsonSchema != null && jsonSchema.isNotBlank()) {
            optionsBuilder.responseFormat(
                ResponseFormat(ResponseFormat.Type.JSON_SCHEMA, jsonSchema)
            )
        }

        return Prompt(messages, optionsBuilder.build())
    }
}
