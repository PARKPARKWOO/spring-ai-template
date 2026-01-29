package com.example.demo.adapter.out.client

import com.example.demo.adapter.out.persistence.AiUsageLogsRepository
import com.example.demo.adapter.out.persistence.ClientApiKeyRepository
import com.example.demo.business.QuotaManagementService
import com.example.demo.business.TokenizerService
import com.example.demo.business.exception.AiServiceException
import com.example.demo.model.ApiErrorCode
import com.example.demo.model.Vendor
import com.example.demo.model.api.ApiKey
import com.example.demo.model.api.GoogleApiKey
import com.google.genai.Client
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.google.genai.GoogleGenAiChatModel
import org.springframework.ai.google.genai.GoogleGenAiChatOptions
import org.springframework.ai.google.genai.schema.GoogleGenAiToolCallingManager
import org.springframework.ai.model.tool.ToolCallingChatOptions
import org.springframework.ai.support.ToolCallbacks
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatModel
import org.springframework.ai.vertexai.gemini.api.VertexAiGeminiApi
import org.springframework.util.MimeTypeUtils
import org.springframework.stereotype.Component
import com.example.demo.common.logger
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.content.Media
import java.net.URI

@Component
class GeminiClient(
    private val tokenizerService: TokenizerService,
    private val quotaManagementService: QuotaManagementService,
    private val aiUsageLogsRepository: AiUsageLogsRepository,
    private val clientApiKeyRepository: ClientApiKeyRepository,
    private val vertexAi: VertexAiGeminiChatModel,
): AbstractAiCallTemplate(
    tokenizerService = tokenizerService,
    quotaManagementService = quotaManagementService,
    aiUsageLogsRepository = aiUsageLogsRepository,
    clientApiKeyRepository = clientApiKeyRepository,
) {
    override fun getVendor(): Vendor = Vendor.GOOGLE
    override fun generateModel(apiKey: ApiKey): ChatModel {
        val googleApiKey = apiKey as? GoogleApiKey
            ?: throw AiServiceException(
                ApiErrorCode.AI_API_KEY_NOT_FOUND,
                "Google 벤더에 대한 유효한 API 키 타입이 아닙니다. (API Key ID: ${apiKey.id})"
            )
//        val vertexAi = VertexAI(googleApiKey.projectId, googleApiKey.location)
        val client = Client.builder()
            .apiKey(googleApiKey.apiKey)
            .build()
        
        return GoogleGenAiChatModel.builder()
            .genAiClient(client)
            .build()

//        VertexAiGeminiChatModel.builder()
//            .vertexAI(vertexAi)
//            .build()
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
        // URL Context가 제공되면 마지막 UserMessage에 Media로 추가
        val processedMessages = if (!urlContexts.isNullOrEmpty()) {
            val mediaList = urlContexts.mapNotNull { url ->
                try {
                    Media.builder()
                        .mimeType(MimeTypeUtils.TEXT_HTML)
                        .data(URI.create(url))
                        .build()
                } catch (e: Exception) {
                    logger().warn("Invalid URL in urlContexts: $url", e)
                    null
                }
            }
            
            // 마지막 UserMessage를 찾아서 Media 추가
            val updatedMessages = messages.toMutableList()
            val lastUserMessageIndex = updatedMessages.indexOfLast { it is UserMessage }
            if (lastUserMessageIndex >= 0 && mediaList.isNotEmpty()) {
                val lastUserMessage = updatedMessages[lastUserMessageIndex] as UserMessage
                val userMessageWithMedia = UserMessage.builder()
                    .text(lastUserMessage.text)
                    .media(mediaList)
                    .build()
                updatedMessages[lastUserMessageIndex] = userMessageWithMedia
            }
            updatedMessages
        } else {
            messages
        }
        
        val optionsBuilder = GoogleGenAiChatOptions.builder()
            .maxOutputTokens(maxTokens)

        model?.let { optionsBuilder.model(model) }

        // JSON Schema가 제공되면 Structured Output 설정
        if (jsonSchema != null && jsonSchema.isNotBlank()) {
            optionsBuilder.responseMimeType("application/json")
            optionsBuilder.responseSchema(jsonSchema)
        }
        
        // Google Search Grounding 활성화
        if (enableGoogleSearch == true) {
            optionsBuilder.googleSearchRetrieval(true)
        }
        
        // Tool Calling 설정
        if (!toolNames.isNullOrEmpty()) {
            optionsBuilder.toolNames(toolNames.toSet())
        }
        
        // 캐시된 콘텐츠 사용 설정 (Gemini 전용)
        if (useCachedContent == true) {
            optionsBuilder.useCachedContent(true)
            cachedContentName?.let {
                optionsBuilder.cachedContentName(it)
            }
        }

        return Prompt(processedMessages, optionsBuilder.build())
    }
}