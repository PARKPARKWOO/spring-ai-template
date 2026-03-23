package com.example.demo.adapter.out.client

import com.example.demo.business.TokenizerService
import com.example.demo.common.ratelimit.ApiKeyRateLimiter
import com.example.demo.business.exception.AiServiceException
import com.example.demo.dto.AiCallContext
import com.example.demo.model.ApiErrorCode
import com.example.demo.model.Vendor
import com.example.demo.model.api.ApiKey
import com.example.demo.model.api.XAiApiKey
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
    tokenizerService: TokenizerService,
    apiKeyResolver: ApiKeyResolver,
    rateLimiter: ApiKeyRateLimiter,
) : AbstractAiCallTemplate(tokenizerService, apiKeyResolver, rateLimiter) {
    override fun getVendor(): Vendor = Vendor.X_AI

    override fun generateModel(apiKey: ApiKey): ChatModel {
        val xAiApiKey = apiKey as? XAiApiKey
            ?: throw AiServiceException(
                ApiErrorCode.AI_API_KEY_NOT_FOUND,
                "xAI/Grok 벤더에 대한 유효한 API 키 타입이 아닙니다. (API Key ID: ${apiKey.id})"
            )

        val api = OpenAiApi.builder()
            .apiKey(xAiApiKey.apiKey)
            .baseUrl("https://api.x.ai")
            .completionsPath("/v1/chat/completions")
            .build()

        return OpenAiChatModel.builder()
            .openAiApi(api)
            .defaultOptions(
                OpenAiChatOptions.builder()
                    .model("grok-4")
                    .build()
            )
            .build()
    }

    override fun generatePrompt(context: AiCallContext, messages: List<Message>): Prompt {
        val optionsBuilder = OpenAiChatOptions.builder()
            .maxTokens(context.effectiveMaxTokens())

        context.model?.let { optionsBuilder.model(it) }

        // JSON Schema가 제공되면 Structured Output 설정 (OpenAI API 호환)
        val jsonSchema = context.jsonSchema
        if (jsonSchema != null && jsonSchema.isNotBlank()) {
            optionsBuilder.responseFormat(
                ResponseFormat(ResponseFormat.Type.JSON_SCHEMA, jsonSchema)
            )
        }

        return Prompt(messages, optionsBuilder.build())
    }
}
