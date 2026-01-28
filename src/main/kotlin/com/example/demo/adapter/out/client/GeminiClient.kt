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
        userMessage: String,
        systemPrompt: String,
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
        val systemMessage = SystemMessage(systemPrompt)
        
        // URL Context가 제공되면 Media로 추가
        // Gemini는 URL을 직접 처리할 수 있으며, URI를 Media의 data로 전달하면 됩니다.
        // MIME 타입은 Gemini가 자동으로 감지하거나, 명시적으로 지정할 수 있습니다.
        val mediaList = urlContexts?.mapNotNull { url ->
            try {
                // URL을 Media로 변환
                // Gemini는 URL의 콘텐츠 타입을 자동으로 감지하므로 MIME 타입을 명시하지 않아도 됩니다.
                // 다만, 명시적으로 지정하려면 URL의 실제 콘텐츠 타입을 확인해야 합니다.
                // 여기서는 기본적으로 text/html로 설정하되, 실제로는 Gemini가 자동 감지합니다.
                Media.builder()
                    .mimeType(MimeTypeUtils.TEXT_HTML) // URL 컨텍스트는 기본적으로 HTML로 처리
                    .data(URI.create(url))
                    .build()
            } catch (e: Exception) {
                logger().warn("Invalid URL in urlContexts: $url", e)
                null
            }
        } ?: emptyList()
        
        // UserMessage 생성 (URL Context가 있으면 Media 포함)
        val userMessageObj = if (mediaList.isNotEmpty()) {
            UserMessage.builder()
                .text(userMessage)
                .media(mediaList)
                .build()
        } else {
            UserMessage(userMessage)
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
        // Context7 문서 확인 결과: googleSearchRetrieval(true) 메서드가 올바른 방식입니다.
        // 이 기능은 Gemini가 Google Search를 사용하여 최신 정보를 검색하도록 합니다.
        if (enableGoogleSearch == true) {
            optionsBuilder.googleSearchRetrieval(true)
        }
        
        // Tool Calling 설정
        if (!toolNames.isNullOrEmpty()) {
            optionsBuilder.toolNames(toolNames.toSet())
            // toolCallbacks는 별도로 등록해야 할 수 있음
            // 필요시 ToolCallbackRegistry를 통해 등록
        }
        
        // 캐시된 콘텐츠 사용 설정 (Gemini 전용)
        if (useCachedContent == true) {
            optionsBuilder.useCachedContent(true)
            cachedContentName?.let {
                optionsBuilder.cachedContentName(it)
            }
        }
        
        return Prompt(listOf(userMessageObj, systemMessage), optionsBuilder.build())
    }
}