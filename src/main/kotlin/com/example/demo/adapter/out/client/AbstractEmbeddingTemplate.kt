package com.example.demo.adapter.out.client

import com.example.demo.adapter.out.persistence.AiUsageLogsRepository
import com.example.demo.adapter.out.persistence.ClientApiKeyRepository
import com.example.demo.business.QuotaManagementService
import com.example.demo.business.TokenizerService
import com.example.demo.business.exception.EmbeddingServiceException
import com.example.demo.common.logger
import com.example.demo.dto.EmbeddingVendorOptions
import com.example.demo.model.AiUsageLogs
import com.example.demo.model.ApiErrorCode
import com.example.demo.model.ContentType
import com.example.demo.model.Vendor
import com.example.demo.model.api.ApiKey
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.embedding.EmbeddingOptions
import org.springframework.ai.embedding.EmbeddingRequest
import org.springframework.ai.embedding.EmbeddingResponse

/**
 * Embedding 모델 호출을 위한 추상 템플릿 클래스
 * AbstractAiCallTemplate과 동일한 패턴으로 구현
 */
abstract class AbstractEmbeddingTemplate(
    private val clientApiKeyRepository: ClientApiKeyRepository,
    private val tokenizerService: TokenizerService,
    private val quotaManagementService: QuotaManagementService,
    private val aiUsageLogsRepository: AiUsageLogsRepository,
) : EmbeddingPort {

    abstract fun getVendor(): Vendor
    protected abstract fun generateEmbeddingModel(apiKey: ApiKey, model: String?): EmbeddingModel
    protected abstract fun generateEmbeddingOptions(
        model: String?,
        vendorOptions: EmbeddingVendorOptions?,
    ): EmbeddingOptions?

    override suspend fun embed(
        text: String,
        clientId: Long,
        model: String?,
        vendorOptions: EmbeddingVendorOptions?,
    ): FloatArray {
        return embed(listOf(text), clientId, model, vendorOptions).first()
    }

    override suspend fun embed(
        texts: List<String>,
        clientId: Long,
        model: String?,
        vendorOptions: EmbeddingVendorOptions?,
    ): List<FloatArray> {
        val response = embedForResponse(texts, clientId, model, vendorOptions)
        return response.results.map { it.output }
    }

    override suspend fun embedForResponse(
        texts: List<String>,
        clientId: Long,
        model: String?,
        vendorOptions: EmbeddingVendorOptions?,
    ): EmbeddingResponse {
        val vendor = getVendor()
        val modelName = model ?: "default"
        
        // 입력 텍스트의 토큰 수 계산 (모든 텍스트 합계)
        // Embedding은 systemPrompt가 없으므로 빈 문자열 사용
        val totalInputTokens = texts.sumOf { text ->
            tokenizerService.getTokenCount(text, "").toLong()
        }.toInt()
        
        logger().info("Embedding quota allocation - clientId: $clientId, vendor: $vendor, model: $modelName, inputTokens: $totalInputTokens")

        // 새로운 통합 쿼터 서비스로 할당 (가격 기반 + 토큰 기반)
        // Embedding은 출력 토큰이 없으므로 outputTokens = 0
        runCatching {
            quotaManagementService.allocateQuota(
                clientId = clientId,
                vendor = vendor,
                model = modelName,
                inputTokens = totalInputTokens,
                outputTokens = 0, // Embedding은 출력 토큰이 없음
                contentType = ContentType.EMBEDDING,
            )
        }.getOrElse {
            logger().error("Embedding quota allocation failed - clientId: $clientId, vendor: $vendor", it)
            throw EmbeddingServiceException(
                ApiErrorCode.EMBEDDING_QUOTA_EXCEEDED,
                it.message ?: "쿼터 할당에 실패했습니다."
            )
        }

        // clientId와 vendor로 사용 가능한 ApiKey 조회
        val clientApiKey = clientApiKeyRepository.findActiveApiKeyByClientIdAndVendor(clientId, vendor)
            ?: throw EmbeddingServiceException(
                ApiErrorCode.EMBEDDING_API_KEY_NOT_FOUND,
                "클라이언트 ID ${clientId}에 대한 ${vendor} 벤더의 활성화된 Embedding API 키를 찾을 수 없습니다."
            )

        val apiKey = clientApiKey.apiKey
        logger().info("Using API key for embedding - clientId: $clientId, vendor: $vendor, apiKeyId: ${apiKey.id}, model: $modelName")

        val embeddingModel: EmbeddingModel = generateEmbeddingModel(apiKey, model)

        // 벤더별 옵션 생성
        val embeddingOptions = generateEmbeddingOptions(model, vendorOptions)

        val response = runCatching {
            if (embeddingOptions != null) {
                // 옵션이 있으면 EmbeddingRequest에 포함
                embeddingModel.call(EmbeddingRequest(texts, embeddingOptions))
            } else {
                // 옵션이 없으면 기본 호출
                embeddingModel.embedForResponse(texts)
            }
        }.getOrElse { error ->
            logger().error("Embedding error for clientId: $clientId, vendor: $vendor", error)
            // 실패 시 할당된 쿼터 롤백
            runCatching {
                quotaManagementService.adjustByActualUsage(
                    clientId = clientId,
                    vendor = vendor,
                    model = modelName,
                    contentType = ContentType.EMBEDDING,
                    allocatedInputTokens = totalInputTokens,
                    allocatedOutputTokens = 0,
                    actualInputTokens = 0,
                    actualOutputTokens = 0,
                )
            }
            throw error
        }

        // EmbeddingResponse에서 실제 토큰 사용량 확인 (있는 경우)
        // Spring AI의 EmbeddingResponse는 metadata.usage를 제공하지 않을 수 있으므로,
        // 입력 토큰 수를 실제 사용량으로 사용
        val actualInputTokens = response.metadata?.usage?.promptTokens ?: totalInputTokens
        val actualOutputTokens = response.metadata?.usage?.completionTokens ?: 0

        logger().info(
            "Embedding completed - clientId: $clientId, vendor: $vendor, texts: ${texts.size}, " +
            "dimensions: ${response.results.firstOrNull()?.output?.size ?: 0}, " +
            "inputTokens: $actualInputTokens, outputTokens: $actualOutputTokens"
        )

        // 실제 사용량으로 쿼터 조정
        runCatching {
            quotaManagementService.adjustByActualUsage(
                clientId = clientId,
                vendor = vendor,
                model = modelName,
                contentType = ContentType.EMBEDDING,
                allocatedInputTokens = totalInputTokens,
                allocatedOutputTokens = 0,
                actualInputTokens = actualInputTokens,
                actualOutputTokens = actualOutputTokens,
            )
        }

        // Embedding 사용 로그 저장
        val combinedText = texts.joinToString("\n")
        save(
            vendor = vendor,
            clientId = clientId,
            model = modelName,
            requestMessage = combinedText,
            responseMessage = "Embedding vectors generated (${response.results.size} vectors)",
            promptToken = actualInputTokens,
            completionToken = actualOutputTokens,
            sessionId = "embedding-${clientId}", // Embedding은 세션 개념이 없으므로 임시 ID 사용
        )

        return response
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
            contentType = ContentType.EMBEDDING,
        )
        aiUsageLogsRepository.save(logs)
    }
}
