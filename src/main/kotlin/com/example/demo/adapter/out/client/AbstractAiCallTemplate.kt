package com.example.demo.adapter.out.client

import com.example.demo.adapter.out.persistence.ApiKeyRepository
import com.example.demo.business.TokenizerService
import com.example.demo.business.exception.AiServiceException
import com.example.demo.dto.AiApiResponse
import com.example.demo.dto.AiCallContext
import com.example.demo.dto.ChatMessage
import com.example.demo.model.ApiErrorCode
import com.example.demo.model.Vendor
import com.example.demo.model.api.ApiKey
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.prompt.Prompt
import reactor.core.publisher.Flux

/**
 * AI 벤더 호출 템플릿.
 *
 * Template Method 패턴으로 공통 흐름(메시지 변환 → API 키 조회 → 모델 생성 → 프롬프트 생성 → 호출)을
 * 정의하고, 각 벤더 클라이언트는 generateModel()과 generatePrompt()만 구현하면 된다.
 *
 * AiCallContext를 통해 모든 호출 파라미터를 전달받으므로
 * 새로운 벤더 옵션 추가 시 인터페이스 변경 없이 확장 가능하다.
 */
abstract class AbstractAiCallTemplate(
    private val tokenizerService: TokenizerService,
    private val apiKeyRepository: ApiKeyRepository,
) : AiCallPort {

    abstract override fun getVendor(): Vendor

    /**
     * 벤더별 ChatModel 인스턴스를 생성한다.
     * API 키 타입 검증 및 벤더별 API 클라이언트 초기화를 담당한다.
     */
    protected abstract fun generateModel(apiKey: ApiKey): ChatModel

    /**
     * 벤더별 Prompt를 생성한다.
     * AiCallContext에서 해당 벤더에 필요한 옵션만 추출하여 사용한다.
     */
    protected abstract fun generatePrompt(context: AiCallContext, messages: List<Message>): Prompt

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

    override suspend fun call(context: AiCallContext): AiApiResponse {
        val vendor = getVendor()
        val springAiMessages = convertToSpringAiMessages(context.messages)
        val prompt = generatePrompt(context, springAiMessages)

        val apiKey = resolveApiKey(context.applicationId, vendor)
        val chatModel: ChatModel = generateModel(apiKey)

        val response = runCatching { chatModel.call(prompt) }.getOrElse { cause ->
            val code = when (cause) {
                is java.net.SocketTimeoutException,
                is java.net.ConnectException,
                is java.io.IOException -> ApiErrorCode.AI_NETWORK_ERROR
                else -> ApiErrorCode.AI_MODEL_ERROR
            }
            throw AiServiceException(code, cause.message, cause)
        }
        val result = response.result.output.text ?: "no content"
        return AiApiResponse(vendor, result)
    }

    override suspend fun stream(context: AiCallContext): Flux<String> {
        val vendor = getVendor()
        val springAiMessages = convertToSpringAiMessages(context.messages)
        val prompt = generatePrompt(context, springAiMessages)

        val apiKey = try {
            resolveApiKey(context.applicationId, vendor)
        } catch (e: AiServiceException) {
            return Flux.error(e)
        }
        val chatModel: ChatModel = generateModel(apiKey)
        return chatModel.stream(prompt).map { it.result.output.text ?: "" }
    }

    private fun resolveApiKey(applicationId: String, vendor: Vendor): ApiKey =
        apiKeyRepository.findByApplicationIdAndVendorAndDeletedAtIsNull(applicationId, vendor)
            ?: throw AiServiceException(
                ApiErrorCode.AI_API_KEY_NOT_FOUND,
                "applicationId=$applicationId, vendor=$vendor 에 대한 API 키를 찾을 수 없습니다."
            )
}
