package com.example.demo.adapter.out.client

import com.example.demo.adapter.out.persistence.ApiKeyRepository
import com.example.demo.business.TokenizerService
import com.example.demo.business.exception.AiServiceException
import com.example.demo.model.ApiErrorCode
import com.example.demo.model.Vendor
import com.example.demo.model.api.ApiKey
import com.example.demo.model.api.GoogleApiKey
import com.google.genai.Client
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.google.genai.GoogleGenAiChatModel
import org.springframework.ai.google.genai.GoogleGenAiChatOptions
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatModel
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class GeminiClient(
    tokenizerService: TokenizerService,
    apiKeyRepository: ApiKeyRepository,
    private val vertexAi: VertexAiGeminiChatModel,
    private val urlFetchRestClient: RestClient,
) : AbstractAiCallTemplate(tokenizerService, apiKeyRepository) {

    private val log = LoggerFactory.getLogger(GeminiClient::class.java)

    override fun getVendor(): Vendor = Vendor.GOOGLE

    override fun generateModel(apiKey: ApiKey): ChatModel {
        val googleApiKey =
            apiKey as? GoogleApiKey
                ?: throw AiServiceException(
                    ApiErrorCode.AI_API_KEY_NOT_FOUND,
                    "Google 벤더에 대한 유효한 API 키 타입이 아닙니다. (API Key ID: ${apiKey.id})",
                )
//        val vertexAi = VertexAI(googleApiKey.projectId, googleApiKey.location)
        val client =
            Client
                .builder()
                .apiKey(googleApiKey.apiKey)
                .build()

        return GoogleGenAiChatModel
            .builder()
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
        val optionsBuilder =
            GoogleGenAiChatOptions
                .builder()
                .maxOutputTokens(maxTokens)

        // URL Context 처리: URL을 직접 호출해서 내용을 가져와 메시지에 포함
        val processedMessages =
            if (!urlContexts.isNullOrEmpty()) {
                // URL 내용을 비동기로 가져오기 (suspend 함수이므로 실제로는 동기적으로 처리)
                val urlContents =
                    urlContexts.mapNotNull { url ->
                        try {
                            fetchUrlContent(url)
                        } catch (e: Exception) {
                            log.warn("Failed to fetch URL content: $url", e)
                            null
                        }
                    }

                // 마지막 UserMessage를 찾아서 URL 내용 추가
                val updatedMessages = messages.toMutableList()
                val lastUserMessageIndex = updatedMessages.indexOfLast { it is UserMessage }
                if (lastUserMessageIndex >= 0 && urlContents.isNotEmpty()) {
                    val lastUserMessage = updatedMessages[lastUserMessageIndex] as UserMessage

                    // URL 내용을 메시지 텍스트에 추가
                    val urlContentText =
                        urlContents.joinToString("\n\n---\n\n") { (url, content) ->
                            "URL: $url\n\nContent:\n$content"
                        }
                    val updatedText =
                        if (lastUserMessage.text.isNotBlank()) {
                            "${lastUserMessage.text}\n\n--- URL Contexts ---\n\n$urlContentText"
                        } else {
                            "--- URL Contexts ---\n\n$urlContentText"
                        }

                    val userMessageWithUrlContent =
                        UserMessage
                            .builder()
                            .text(updatedText)
                            .build()
                    updatedMessages[lastUserMessageIndex] = userMessageWithUrlContent
                }
                updatedMessages
            } else {
                messages
            }

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

    /**
     * Spring RestClient로 URL 내용을 가져옵니다.
     * 301/302 리다이렉트는 [UrlFetchRestClientConfig]에서 NORMAL로 설정되어 자동 추적됩니다.
     *
     * @param url 가져올 URL
     * @return Pair<URL, Content> URL과 내용의 쌍
     */
    private fun fetchUrlContent(url: String): Pair<String, String> {
        val content = urlFetchRestClient.get()
            .uri(url)
            .retrieve()
            .body(String::class.java)
            ?: throw RuntimeException("Failed to fetch URL: $url, empty body")

        // HTML인 경우 간단한 텍스트 추출 (태그 제거)
        val textContent =
            if (content.contains("<html", ignoreCase = true) ||
                content.contains("<!DOCTYPE", ignoreCase = true)
            ) {
                content
                    .replace(Regex("<script[^>]*>.*?</script>", RegexOption.DOT_MATCHES_ALL), "")
                    .replace(Regex("<style[^>]*>.*?</style>", RegexOption.DOT_MATCHES_ALL), "")
                    .replace(Regex("<[^>]+>"), "")
                    .replace(Regex("\\s+"), " ")
                    .trim()
            } else {
                content
            }

        // 내용이 너무 길면 잘라내기 (예: 50000자 제한)
        val maxLength = 50000
        val finalContent =
            if (textContent.length > maxLength) {
                textContent.take(maxLength) + "\n\n[Content truncated due to length]"
            } else {
                textContent
            }

        return Pair(url, finalContent)
    }
}
