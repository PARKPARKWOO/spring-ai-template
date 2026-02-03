package com.example.demo.adapter.out.client

import com.example.demo.adapter.out.persistence.ApiKeyRepository
import com.example.demo.business.TokenizerService
import com.example.demo.business.exception.AiServiceException
import com.example.demo.dto.AiApiResponse
import com.example.demo.dto.ChatMessage
import com.example.demo.model.ApiErrorCode
import com.example.demo.model.Vendor
import com.example.demo.model.api.ApiKey
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.prompt.Prompt
import reactor.core.publisher.Flux
import kotlin.jvm.Throws

abstract class AbstractAiCallTemplate(
    private val tokenizerService: TokenizerService,
    private val apiKeyRepository: ApiKeyRepository,
) : AiCallPort {
    companion object {
        private const val DEFAULT_SYSTEM_PROMPT = """
            이 응답에 대해서 맞고 틀리는 부분에 대해서 검증해줘 만약 틀렸다면 어디가 왜 틀렸는지 증명해
        """
        private const val FINANCIAL_MAX_TOKEN = 2000
    }

    abstract fun getVendor(): Vendor
    protected abstract fun generateModel(apiKey: ApiKey): ChatModel
    protected abstract fun generatePrompt(
        messages: List<Message>,
        maxTokens: Int,
        jsonSchema: String? = null,
        urlContexts: List<String>? = null,
        enableGoogleSearch: Boolean? = null,
        toolNames: List<String>? = null,
        model: String?,
        useCachedContent: Boolean? = null,
        cachedContentName: String? = null,
        cacheStrategy: String? = null,
        cacheTtl: String? = null,
    ): Prompt

    protected fun convertToSpringAiMessages(chatMessages: List<ChatMessage>): List<Message> =
        chatMessages.map { msg ->
            when (msg) {
                is com.example.demo.dto.UserChatMessage -> UserMessage(msg.content)
                is com.example.demo.dto.SystemChatMessage -> SystemMessage(msg.content)
                is com.example.demo.dto.AssistantChatMessage -> AssistantMessage(msg.content)
            }
        }

    protected fun calculateTokenCountFromMessages(messages: List<Message>): Int {
        val text = messages.joinToString(" ") { it.text }
        return tokenizerService.getTokenCount(text, "")
    }

    override suspend fun call(userMessage: String, applicationId: String, sessionId: String, model: String?): AiApiResponse =
        call(userMessage, DEFAULT_SYSTEM_PROMPT, applicationId, sessionId, null, null, null, null, model)

    @Throws(AiServiceException::class)
    override suspend fun call(
        userMessage: String,
        systemPrompt: String,
        applicationId: String,
        sessionId: String,
        jsonSchema: String?,
        urlContexts: List<String>?,
        enableGoogleSearch: Boolean?,
        toolNames: List<String>?,
        model: String?,
        useCachedContent: Boolean?,
        cachedContentName: String?,
        cacheStrategy: String?,
        cacheTtl: String?,
    ): AiApiResponse {
        val vendor = getVendor()
        val modelName = model ?: "default"
        val messages = mutableListOf<Message>().apply {
            if (systemPrompt.isNotBlank()) add(SystemMessage(systemPrompt))
            add(UserMessage(userMessage))
        }
        val prompt = generatePrompt(messages, FINANCIAL_MAX_TOKEN, jsonSchema, urlContexts, enableGoogleSearch, toolNames, model, useCachedContent, cachedContentName, cacheStrategy, cacheTtl)

        val apiKey = apiKeyRepository.findByApplicationIdAndVendorAndDeletedAtIsNull(applicationId, vendor)
            ?: throw AiServiceException(ApiErrorCode.AI_API_KEY_NOT_FOUND, "applicationId=$applicationId, vendor=$vendor 에 대한 API 키를 찾을 수 없습니다.")
        val chatModel: ChatModel = generateModel(apiKey)

        val response = runCatching { chatModel.call(prompt) }.getOrElse { cause ->
            val code = when (cause) {
                is java.net.SocketTimeoutException, is java.net.ConnectException, is java.io.IOException -> ApiErrorCode.AI_NETWORK_ERROR
                else -> ApiErrorCode.AI_MODEL_ERROR
            }
            throw AiServiceException(code, cause.message, cause)
        }
        val result = response.result.output.text ?: "no content"
        return AiApiResponse(vendor, result)
    }

    override suspend fun call(
        messages: List<ChatMessage>,
        applicationId: String,
        sessionId: String,
        jsonSchema: String?,
        urlContexts: List<String>?,
        enableGoogleSearch: Boolean?,
        toolNames: List<String>?,
        model: String?,
        useCachedContent: Boolean?,
        cachedContentName: String?,
        cacheStrategy: String?,
        cacheTtl: String?,
    ): AiApiResponse {
        val vendor = getVendor()
        val modelName = model ?: "default"
        val springAiMessages = convertToSpringAiMessages(messages)
        val prompt = generatePrompt(springAiMessages, FINANCIAL_MAX_TOKEN, jsonSchema, urlContexts, enableGoogleSearch, toolNames, model, useCachedContent, cachedContentName, cacheStrategy, cacheTtl)

        val apiKey = apiKeyRepository.findByApplicationIdAndVendorAndDeletedAtIsNull(applicationId, vendor)
            ?: throw AiServiceException(ApiErrorCode.AI_API_KEY_NOT_FOUND, "applicationId=$applicationId, vendor=$vendor 에 대한 API 키를 찾을 수 없습니다.")
        val chatModel: ChatModel = generateModel(apiKey)

        val response = runCatching { chatModel.call(prompt) }.getOrElse { cause ->
            val code = when (cause) {
                is java.net.SocketTimeoutException, is java.net.ConnectException, is java.io.IOException -> ApiErrorCode.AI_NETWORK_ERROR
                else -> ApiErrorCode.AI_MODEL_ERROR
            }
            throw AiServiceException(code, cause.message, cause)
        }
        val result = response.result.output.text ?: "no content"
        return AiApiResponse(vendor, result)
    }

    override suspend fun stream(
        userMessage: String,
        systemPrompt: String,
        applicationId: String,
        sessionId: String,
        jsonSchema: String?,
        urlContexts: List<String>?,
        enableGoogleSearch: Boolean?,
        toolNames: List<String>?,
        model: String?,
        useCachedContent: Boolean?,
        cachedContentName: String?,
        cacheStrategy: String?,
        cacheTtl: String?,
    ): Flux<String> {
        val vendor = getVendor()
        val modelName = model ?: "default"
        val messages = mutableListOf<Message>().apply {
            if (systemPrompt.isNotBlank()) add(SystemMessage(systemPrompt))
            add(UserMessage(userMessage))
        }
        val prompt = generatePrompt(messages, FINANCIAL_MAX_TOKEN, jsonSchema, urlContexts, enableGoogleSearch, toolNames, model, useCachedContent, cachedContentName, cacheStrategy, cacheTtl)

        val apiKey = apiKeyRepository.findByApplicationIdAndVendorAndDeletedAtIsNull(applicationId, vendor)
            ?: return Flux.error(AiServiceException(ApiErrorCode.AI_API_KEY_NOT_FOUND, "applicationId=$applicationId, vendor=$vendor 에 대한 API 키를 찾을 수 없습니다."))
        val chatModel: ChatModel = generateModel(apiKey)
        return chatModel.stream(prompt).map { it.result.output.text ?: "" }
    }

    override suspend fun stream(
        messages: List<ChatMessage>,
        applicationId: String,
        sessionId: String,
        jsonSchema: String?,
        urlContexts: List<String>?,
        enableGoogleSearch: Boolean?,
        toolNames: List<String>?,
        model: String?,
        useCachedContent: Boolean?,
        cachedContentName: String?,
        cacheStrategy: String?,
        cacheTtl: String?,
    ): Flux<String> {
        val vendor = getVendor()
        val springAiMessages = convertToSpringAiMessages(messages)
        val prompt = generatePrompt(springAiMessages, FINANCIAL_MAX_TOKEN, jsonSchema, urlContexts, enableGoogleSearch, toolNames, model, useCachedContent, cachedContentName, cacheStrategy, cacheTtl)

        val apiKey = apiKeyRepository.findByApplicationIdAndVendorAndDeletedAtIsNull(applicationId, vendor)
            ?: return Flux.error(AiServiceException(ApiErrorCode.AI_API_KEY_NOT_FOUND, "applicationId=$applicationId, vendor=$vendor 에 대한 API 키를 찾을 수 없습니다."))
        val chatModel: ChatModel = generateModel(apiKey)
        return chatModel.stream(prompt).map { it.result.output.text ?: "" }
    }
}
