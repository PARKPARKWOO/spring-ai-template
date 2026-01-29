package com.example.demo.adapter.out.client

import com.example.demo.adapter.out.persistence.AiUsageLogsRepository
import com.example.demo.adapter.out.persistence.ClientApiKeyRepository
import com.example.demo.business.QuotaManagementService
import com.example.demo.business.TokenizerService
import com.example.demo.business.exception.AiServiceException
import com.example.demo.common.logger
import com.example.demo.dto.AiApiResponse
import com.example.demo.dto.ChatMessage
import com.example.demo.model.AiUsageLogs
import com.example.demo.model.ApiErrorCode
import com.example.demo.model.ContentType
import com.example.demo.model.Vendor
import com.example.demo.model.api.ApiKey
import org.slf4j.Logger
import org.springframework.ai.chat.memory.ChatMemoryRepository
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
    private val quotaManagementService: QuotaManagementService, // 새로운 통합 쿼터 서비스
    private val aiUsageLogsRepository: AiUsageLogsRepository,
    private val clientApiKeyRepository: ClientApiKeyRepository,
) : AiCallPort {
    companion object {
        private const val DEFAULT_SYSTEM_PROMPT = """
            이 응답에 대해서 맞고 틀리는 부분에 대해서 검증해줘 만약 틀렸다면 어디가 왜 틀렸는지 증명해
        """
        private const val FINANCIAL_MAX_TOKEN = 2000
    }

    abstract fun getVendor(): Vendor
    protected abstract fun generateModel(apiKey: ApiKey): ChatModel
    
    /**
     * 대화 내역 메시지 목록으로 Prompt 생성 (통합 메서드)
     */
    protected abstract fun generatePrompt(
        messages: List<Message>,
        maxTokens: Int,
        jsonSchema: String? = null,
        urlContexts: List<String>? = null,
        enableGoogleSearch: Boolean? = null,
        toolNames: List<String>? = null,
        model: String?,
        // Gemini 캐시 옵션
        useCachedContent: Boolean? = null,
        cachedContentName: String? = null,
        // Anthropic 캐시 옵션
        cacheStrategy: String? = null,
        cacheTtl: String? = null,
    ): Prompt
    
    /**
     * ChatMessage DTO를 Spring AI의 Message로 변환
     */
    protected fun convertToSpringAiMessages(chatMessages: List<ChatMessage>): List<Message> {
        return chatMessages.map { chatMessage ->
            when (chatMessage) {
                is com.example.demo.dto.UserChatMessage -> UserMessage(chatMessage.content)
                is com.example.demo.dto.SystemChatMessage -> SystemMessage(chatMessage.content)
                is com.example.demo.dto.AssistantChatMessage -> AssistantMessage(chatMessage.content)
            }
        }
    }
    
    /**
     * 메시지 목록에서 토큰 수 계산
     */
    protected fun calculateTokenCountFromMessages(messages: List<Message>): Int {
        val text = messages.joinToString(" ") { message ->
            message.text
        }
        return tokenizerService.getTokenCount(text, "")
    }

    // List 요청시 처리를 어떻게 하면 좋을지??
    // 토큰 오차율 최대 10% (앤트로픽 기준) 241/238
    override suspend fun call(userMessage: String, clientId: Long, sessionId: String, model: String?): AiApiResponse {
        return call(userMessage, DEFAULT_SYSTEM_PROMPT, clientId, sessionId, null, null, null, null, model)
    }

    @Throws(AiServiceException::class)
    override suspend fun call(
        userMessage: String,
        systemPrompt: String,
        clientId: Long,
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
        val tokenCount = tokenizerService.getTokenCount(userMessage, systemPrompt)
        val modelName = model ?: "default"

        // 새로운 통합 쿼터 서비스로 할당 (가격 기반 + 토큰 기반)
        runCatching {
            quotaManagementService.allocateQuota(
                clientId = clientId,
                vendor = vendor,
                model = modelName,
                inputTokens = FINANCIAL_MAX_TOKEN,
                outputTokens = 0, // 예상 output 토큰은 0으로 설정 (실제 사용 후 조정)
                contentType = ContentType.TEXT,
            )
        }.getOrElse {
            throw AiServiceException(ApiErrorCode.COMMON_INTERNAL_SERVER_ERROR)
        }

        // 단일 메시지를 List<Message>로 변환
        val messages = mutableListOf<Message>()
        if (systemPrompt.isNotBlank()) {
            messages.add(SystemMessage(systemPrompt))
        }
        messages.add(UserMessage(userMessage))
        
        val prompt = generatePrompt(
            messages,
            FINANCIAL_MAX_TOKEN,
            jsonSchema,
            urlContexts,
            enableGoogleSearch,
            toolNames,
            model,
            useCachedContent,
            cachedContentName,
            cacheStrategy,
            cacheTtl
        )
        logger().info("prompt token is $tokenCount")

        // clientId와 vendor로 사용 가능한 ApiKey 조회
        val clientApiKey = clientApiKeyRepository.findActiveApiKeyByClientIdAndVendor(clientId, vendor)
            ?: throw AiServiceException(
                ApiErrorCode.AI_API_KEY_NOT_FOUND,
                "클라이언트 ID ${clientId}에 대한 ${vendor} 벤더의 활성화된 API 키를 찾을 수 없습니다."
            )

        val apiKey = clientApiKey.apiKey
        logger().info("Using API key for clientId: $clientId, vendor: $vendor, apiKeyId: ${apiKey.id}")
        val chatModel: ChatModel = generateModel(apiKey)

        val response = callWithQuotaRollback(
            quotaManagementService = quotaManagementService,
            logger = logger(),
            clientId = clientId,
            vendor = vendor,
            modelName = modelName,
            tokenCount = FINANCIAL_MAX_TOKEN,
        ) {
            chatModel.call(prompt)
        }

        val inputToken = response.metadata.usage.promptTokens
        val outputToken = response.metadata.usage.completionTokens
        val totalToken = response.metadata.usage.totalTokens
        logger().info("total token count = $totalToken prompt Token = $inputToken outputToken = $outputToken")


        // output.text가 null이거나 비어있는 경우
        val result = response.result.output.text ?: run {
            logger().warn("Generation.output.text is null or empty for vendor: $vendor, clientId: $clientId, model: $modelName")
            "no content" // 기본값 반환
        }
        val finalModelName = prompt.options?.model ?: modelName

        // 정상적인 경우에만 실제 사용량으로 쿼터 조정
        // (예외 발생 시에는 위의 if 블록에서 이미 롤백하고 throw하므로 여기까지 도달하지 않음)
        runCatching {
            quotaManagementService.adjustByActualUsage(
                clientId = clientId,
                vendor = vendor,
                model = finalModelName,
                contentType = ContentType.TEXT,
                allocatedInputTokens = FINANCIAL_MAX_TOKEN,
                allocatedOutputTokens = 0,
                actualInputTokens = inputToken,
                actualOutputTokens = outputToken,
            )
        }

        // Prompt에서 사용자 메시지 추출 (단일 메시지인 경우)
        val userMessageText = prompt.instructions
            .filterIsInstance<UserMessage>()
            .lastOrNull()
            ?.text
            ?: userMessage
        
        save(vendor, clientId, finalModelName, userMessageText, result, inputToken, outputToken, sessionId)
        return AiApiResponse(vendor, result)
    }

    /**
     * 대화 내역 메시지 목록을 받아서 AI 응답을 받습니다.
     */
    override suspend fun call(
        messages: List<ChatMessage>,
        clientId: Long,
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
        val springAiMessages = convertToSpringAiMessages(messages)
        val tokenCount = calculateTokenCountFromMessages(springAiMessages)
        val modelName = model ?: "default"

        // 새로운 통합 쿼터 서비스로 할당 (가격 기반 + 토큰 기반)
        runCatching {
            quotaManagementService.allocateQuota(
                clientId = clientId,
                vendor = vendor,
                model = modelName,
                inputTokens = FINANCIAL_MAX_TOKEN,
                outputTokens = 0,
                contentType = ContentType.TEXT,
            )
        }.getOrElse {
            throw AiServiceException(ApiErrorCode.COMMON_INTERNAL_SERVER_ERROR)
        }

        val prompt = generatePrompt(
            springAiMessages,
            FINANCIAL_MAX_TOKEN,
            jsonSchema,
            urlContexts,
            enableGoogleSearch,
            toolNames,
            model,
            useCachedContent,
            cachedContentName,
            cacheStrategy,
            cacheTtl
        )
        logger().info("prompt token is $tokenCount (from messages)")

        // clientId와 vendor로 사용 가능한 ApiKey 조회
        val clientApiKey = clientApiKeyRepository.findActiveApiKeyByClientIdAndVendor(clientId, vendor)
            ?: throw AiServiceException(
                ApiErrorCode.AI_API_KEY_NOT_FOUND,
                "클라이언트 ID ${clientId}에 대한 ${vendor} 벤더의 활성화된 API 키를 찾을 수 없습니다."
            )

        val apiKey = clientApiKey.apiKey
        logger().info("Using API key for clientId: $clientId, vendor: $vendor, apiKeyId: ${apiKey.id}")
        val chatModel: ChatModel = generateModel(apiKey)

        val response = callWithQuotaRollback(
            quotaManagementService = quotaManagementService,
            logger = logger(),
            clientId = clientId,
            vendor = vendor,
            modelName = modelName,
            tokenCount = FINANCIAL_MAX_TOKEN,
        ) {
            chatModel.call(prompt)
        }

        val inputToken = response.metadata.usage.promptTokens
        val outputToken = response.metadata.usage.completionTokens
        val totalToken = response.metadata.usage.totalTokens
        logger().info("total token count = $totalToken prompt Token = $inputToken outputToken = $outputToken")

        // output.text가 null이거나 비어있는 경우
        val result = response.result.output.text ?: run {
            logger().warn("Generation.output.text is null or empty for vendor: $vendor, clientId: $clientId, model: $modelName")
            "no content"
        }
        val finalModelName = prompt.options?.model ?: modelName

        // 정상적인 경우에만 실제 사용량으로 쿼터 조정
        runCatching {
            quotaManagementService.adjustByActualUsage(
                clientId = clientId,
                vendor = vendor,
                model = finalModelName,
                contentType = ContentType.TEXT,
                allocatedInputTokens = FINANCIAL_MAX_TOKEN,
                allocatedOutputTokens = 0,
                actualInputTokens = inputToken,
                actualOutputTokens = outputToken,
            )
        }

        // 전체 대화 내역을 문자열로 변환 (로그용)
        val conversationHistory = messages.joinToString("\n") { msg ->
            when (msg) {
                is com.example.demo.dto.UserChatMessage -> "User: ${msg.content}"
                is com.example.demo.dto.SystemChatMessage -> "System: ${msg.content}"
                is com.example.demo.dto.AssistantChatMessage -> "Assistant: ${msg.content}"
            }
        }
        save(vendor, clientId, finalModelName, conversationHistory, result, inputToken, outputToken, sessionId)
        return AiApiResponse(vendor, result)
    }

    override suspend fun stream(
        userMessage: String,
        systemPrompt: String,
        clientId: Long,
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
        val tokenCount = tokenizerService.getTokenCount(userMessage, systemPrompt)
        val modelName = model ?: "default"

        // 새로운 통합 쿼터 서비스로 할당 (가격 기반 + 토큰 기반)
        runCatching {
            quotaManagementService.allocateQuota(
                clientId = clientId,
                vendor = vendor,
                model = modelName,
                inputTokens = FINANCIAL_MAX_TOKEN,
                outputTokens = 0, // 예상 output 토큰은 0으로 설정 (실제 사용 후 조정)
                contentType = ContentType.TEXT,
            )
        }.getOrElse { exception ->
            return Flux.error(
                AiServiceException(
                    ApiErrorCode.AI_QUOTA_ALLOCATION_FAILED,
                    exception.message ?: "쿼터 할당에 실패했습니다.",
                    exception
                )
            )
        }

        // 단일 메시지를 List<Message>로 변환
        val messages = mutableListOf<Message>()
        if (systemPrompt.isNotBlank()) {
            messages.add(SystemMessage(systemPrompt))
        }
        messages.add(UserMessage(userMessage))
        
        val prompt = generatePrompt(
            messages,
            FINANCIAL_MAX_TOKEN,
            jsonSchema,
            urlContexts,
            enableGoogleSearch,
            toolNames,
            model,
            useCachedContent,
            cachedContentName,
            cacheStrategy,
            cacheTtl
        )
        logger().info("prompt token is $tokenCount (streaming)")

        // clientId와 vendor로 사용 가능한 ApiKey 조회
        val clientApiKey = clientApiKeyRepository.findActiveApiKeyByClientIdAndVendor(clientId, vendor)
            ?: return Flux.error(
                AiServiceException(
                    ApiErrorCode.AI_API_KEY_NOT_FOUND,
                    "클라이언트 ID ${clientId}에 대한 ${vendor} 벤더의 활성화된 API 키를 찾을 수 없습니다."
                )
            )
        val apiKey = clientApiKey.apiKey
        logger().info("Using API key for clientId: $clientId, vendor: $vendor, apiKeyId: ${apiKey.id} (streaming)")
        val chatModel: ChatModel = generateModel(apiKey)

        // Spring AI의 stream() 메서드 사용 - Flux<ChatResponse> 반환
        val streamResponse: Flux<ChatResponse> = callWithQuotaRollback(
            quotaManagementService = quotaManagementService,
            logger = logger(),
            clientId = clientId,
            vendor = vendor,
            modelName = modelName,
            tokenCount = FINANCIAL_MAX_TOKEN,
        ) {
            chatModel.stream(prompt)
        }


        // 스트림을 두 개로 분리: 하나는 클라이언트 전송용, 하나는 저장용
        val finalModelName = prompt.options?.model ?: modelName

        // 최종 응답 텍스트와 토큰 사용량을 수집하기 위한 변수
        val responseTextBuilder = StringBuilder()
        var totalInputToken = 0
        var totalOutputToken = 0
        var lastChatResponse: ChatResponse? = null

        // ChatResponse에서 텍스트만 추출하여 Flux<String>으로 변환
        val contentStream = streamResponse
            .doOnNext { response ->
                // 각 청크의 텍스트 수집
                val chunkText = response.result.output.text ?: ""
                responseTextBuilder.append(chunkText)

                // 토큰 사용량 누적 (각 청크마다 토큰 정보가 있을 수 있음)
                // 주의: 일부 벤더는 마지막 청크에만 전체 토큰 정보를 제공할 수 있음
                val usage = response.metadata?.usage
                if (usage != null) {
                    // 마지막 응답의 토큰 정보를 사용 (전체 토큰 사용량)
                    lastChatResponse = response
                    totalInputToken = usage.promptTokens
                    totalOutputToken = usage.completionTokens
                }
            }
            .map { response ->
                response.result.output.text ?: ""
            }
            .doOnError { error ->
                logger().error("Stream error for clientId: $clientId, vendor: $vendor", error)
            }
            .doFinally { signalType ->
                // 스트림 완료 후 대화 내역 저장
                try {
                    val finalResponseText = responseTextBuilder.toString()

                    // 마지막 ChatResponse에서 토큰 정보를 다시 확인
                    lastChatResponse?.let { lastResponse ->
                        val usage = lastResponse.metadata?.usage
                        if (usage != null) {
                            totalInputToken = usage.promptTokens
                            totalOutputToken = usage.completionTokens
                        }
                    }

                    // 최종 토큰 사용량 계산
                    val finalInputToken = if (totalInputToken > 0) totalInputToken else tokenCount
                    val finalOutputToken = if (totalOutputToken > 0) totalOutputToken else 0
                    val finalModelNameForSave = prompt.options?.model ?: modelName

                    // 실제 사용량으로 쿼터 조정
                    runCatching {
                        quotaManagementService.adjustByActualUsage(
                            clientId = clientId,
                            vendor = vendor,
                            model = finalModelNameForSave,
                            contentType = ContentType.TEXT,
                            allocatedInputTokens = FINANCIAL_MAX_TOKEN,
                            allocatedOutputTokens = 0,
                            actualInputTokens = finalInputToken,
                            actualOutputTokens = finalOutputToken,
                        )
                    }

                    // 대화 내역 저장
                    if (finalResponseText.isNotEmpty() || (finalInputToken + finalOutputToken) > 0) {
                        // Prompt에서 마지막 사용자 메시지 추출
                        val lastUserMessageText = prompt.instructions
                            .filterIsInstance<UserMessage>()
                            .lastOrNull()
                            ?.text
                            ?: userMessage
                        
                        save(
                            vendor = vendor,
                            clientId = clientId,
                            model = finalModelNameForSave,
                            requestMessage = lastUserMessageText,
                            responseMessage = finalResponseText,
                            promptToken = finalInputToken,
                            completionToken = finalOutputToken,
                            sessionId = sessionId
                        )
                        logger().info("Stream saved: clientId=$clientId, vendor=$vendor, inputToken=$finalInputToken, outputToken=$finalOutputToken, totalToken=${finalInputToken + finalOutputToken}")
                    }
                } catch (e: Exception) {
                    logger().error("Failed to save stream logs for clientId: $clientId, vendor: $vendor", e)
                }
            }

        return contentStream
    }

    /**
     * 대화 내역 메시지 목록을 받아서 스트리밍 방식으로 AI 응답을 받습니다.
     */
    override suspend fun stream(
        messages: List<ChatMessage>,
        clientId: Long,
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
        val tokenCount = calculateTokenCountFromMessages(springAiMessages)
        val modelName = model ?: "default"

        // 새로운 통합 쿼터 서비스로 할당 (가격 기반 + 토큰 기반)
        runCatching {
            quotaManagementService.allocateQuota(
                clientId = clientId,
                vendor = vendor,
                model = modelName,
                inputTokens = FINANCIAL_MAX_TOKEN,
                outputTokens = 0,
                contentType = ContentType.TEXT,
            )
        }.getOrElse { exception ->
            return Flux.error(
                AiServiceException(
                    ApiErrorCode.AI_QUOTA_ALLOCATION_FAILED,
                    exception.message ?: "쿼터 할당에 실패했습니다.",
                    exception
                )
            )
        }

        val prompt = generatePrompt(
            springAiMessages,
            FINANCIAL_MAX_TOKEN,
            jsonSchema,
            urlContexts,
            enableGoogleSearch,
            toolNames,
            model,
            useCachedContent,
            cachedContentName,
            cacheStrategy,
            cacheTtl
        )
        logger().info("prompt token is $tokenCount (streaming from messages)")

        // clientId와 vendor로 사용 가능한 ApiKey 조회
        val clientApiKey = clientApiKeyRepository.findActiveApiKeyByClientIdAndVendor(clientId, vendor)
            ?: return Flux.error(
                AiServiceException(
                    ApiErrorCode.AI_API_KEY_NOT_FOUND,
                    "클라이언트 ID ${clientId}에 대한 ${vendor} 벤더의 활성화된 API 키를 찾을 수 없습니다."
                )
            )
        val apiKey = clientApiKey.apiKey
        logger().info("Using API key for clientId: $clientId, vendor: $vendor, apiKeyId: ${apiKey.id} (streaming)")
        val chatModel: ChatModel = generateModel(apiKey)

        // Spring AI의 stream() 메서드 사용 - Flux<ChatResponse> 반환
        val streamResponse: Flux<ChatResponse> = callWithQuotaRollback(
            quotaManagementService = quotaManagementService,
            logger = logger(),
            clientId = clientId,
            vendor = vendor,
            modelName = modelName,
            tokenCount = FINANCIAL_MAX_TOKEN,
        ) {
            chatModel.stream(prompt)
        }

        // 스트림을 두 개로 분리: 하나는 클라이언트 전송용, 하나는 저장용
        val finalModelName = prompt.options?.model ?: modelName

        // 최종 응답 텍스트와 토큰 사용량을 수집하기 위한 변수
        val responseTextBuilder = StringBuilder()
        var totalInputToken = 0
        var totalOutputToken = 0
        var lastChatResponse: ChatResponse? = null

        // ChatResponse에서 텍스트만 추출하여 Flux<String>으로 변환
        val contentStream = streamResponse
            .doOnNext { response ->
                val chunkText = response.result.output.text ?: ""
                responseTextBuilder.append(chunkText)

                val usage = response.metadata?.usage
                if (usage != null) {
                    lastChatResponse = response
                    totalInputToken = usage.promptTokens
                    totalOutputToken = usage.completionTokens
                }
            }
            .map { response ->
                response.result.output.text ?: ""
            }
            .doOnError { error ->
                logger().error("Stream error for clientId: $clientId, vendor: $vendor", error)
            }
            .doFinally { signalType ->
                try {
                    val finalResponseText = responseTextBuilder.toString()

                    lastChatResponse?.let { lastResponse ->
                        val usage = lastResponse.metadata?.usage
                        if (usage != null) {
                            totalInputToken = usage.promptTokens
                            totalOutputToken = usage.completionTokens
                        }
                    }

                    val finalInputToken = if (totalInputToken > 0) totalInputToken else tokenCount
                    val finalOutputToken = if (totalOutputToken > 0) totalOutputToken else 0
                    val finalModelNameForSave = prompt.options?.model ?: modelName

                    // 실제 사용량으로 쿼터 조정
                    runCatching {
                        quotaManagementService.adjustByActualUsage(
                            clientId = clientId,
                            vendor = vendor,
                            model = finalModelNameForSave,
                            contentType = ContentType.TEXT,
                            allocatedInputTokens = FINANCIAL_MAX_TOKEN,
                            allocatedOutputTokens = 0,
                            actualInputTokens = finalInputToken,
                            actualOutputTokens = finalOutputToken,
                        )
                    }

                    // 대화 내역 저장
                    if (finalResponseText.isNotEmpty() || (finalInputToken + finalOutputToken) > 0) {
                        // 전체 대화 내역을 문자열로 변환 (로그용)
                        val conversationHistory = messages.joinToString("\n") { msg ->
                            when (msg) {
                                is com.example.demo.dto.UserChatMessage -> "User: ${msg.content}"
                                is com.example.demo.dto.SystemChatMessage -> "System: ${msg.content}"
                                is com.example.demo.dto.AssistantChatMessage -> "Assistant: ${msg.content}"
                            }
                        }
                        save(
                            vendor = vendor,
                            clientId = clientId,
                            model = finalModelNameForSave,
                            requestMessage = conversationHistory,
                            responseMessage = finalResponseText,
                            promptToken = finalInputToken,
                            completionToken = finalOutputToken,
                            sessionId = sessionId
                        )
                        logger().info("Stream saved: clientId=$clientId, vendor=$vendor, inputToken=$finalInputToken, outputToken=$finalOutputToken, totalToken=${finalInputToken + finalOutputToken}")
                    }
                } catch (e: Exception) {
                    logger().error("Failed to save stream logs for clientId: $clientId, vendor: $vendor", e)
                }
            }

        return contentStream
    }

    private fun save(
        vendor: Vendor,
        clientId: Long,
        model: String,
        requestMessage: String,
        responseMessage: String,
        promptToken: Int,
        completionToken: Int,
        sessionId: String,
        contentType: ContentType = ContentType.TEXT,
    ) {
        val logs = AiUsageLogs.create(
            vendor = vendor,
            clientId = clientId,
            requestMessage = requestMessage,
            responseMessage = responseMessage,
            model = model,
            promptToken = promptToken,
            completionToken = completionToken,
            sessionId = sessionId,
            contentType = contentType,
        )
        aiUsageLogsRepository.save(logs)
    }
}


inline fun <T> callWithQuotaRollback(
    quotaManagementService: QuotaManagementService,
    logger: Logger,
    clientId: Long,
    vendor: Vendor,
    modelName: String,
    tokenCount: Int,
    crossinline block: () -> T,
): T {
    return runCatching { block() }
        .getOrElse { cause ->
            runCatching { quotaManagementService.adjustByActualUsage(
                clientId = clientId,
                vendor = vendor,
                model = modelName,
                contentType = ContentType.TEXT,
                allocatedInputTokens = tokenCount,
                allocatedOutputTokens = 0,
                actualInputTokens = 0,
                actualOutputTokens = 0,
            ) }.onFailure { logger.error("quota rollback failed", it) }

            val code = when (cause) {
                is java.net.SocketTimeoutException,
                is java.net.ConnectException,
                is java.io.IOException -> ApiErrorCode.AI_NETWORK_ERROR
                else -> ApiErrorCode.AI_MODEL_ERROR
            }
            throw AiServiceException(code)
        }
}