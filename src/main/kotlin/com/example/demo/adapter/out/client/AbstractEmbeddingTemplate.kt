package com.example.demo.adapter.out.client

import com.example.demo.adapter.out.persistence.ApiKeyRepository
import com.example.demo.business.TokenizerService
import com.example.demo.business.exception.EmbeddingServiceException
import com.example.demo.common.logger
import com.example.demo.dto.EmbeddingVendorOptions
import com.example.demo.model.ApiErrorCode
import com.example.demo.model.Vendor
import com.example.demo.model.api.ApiKey
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.embedding.EmbeddingOptions
import org.springframework.ai.embedding.EmbeddingRequest
import org.springframework.ai.embedding.EmbeddingResponse

abstract class AbstractEmbeddingTemplate(
    private val apiKeyRepository: ApiKeyRepository,
    private val tokenizerService: TokenizerService,
) : EmbeddingPort {

    abstract fun getVendor(): Vendor
    protected abstract fun generateEmbeddingModel(apiKey: ApiKey, model: String?): EmbeddingModel
    protected abstract fun generateEmbeddingOptions(model: String?, vendorOptions: EmbeddingVendorOptions?): EmbeddingOptions?

    override suspend fun embed(
        text: String,
        applicationId: String,
        model: String?,
        vendorOptions: EmbeddingVendorOptions?,
    ): FloatArray = embed(listOf(text), applicationId, model, vendorOptions).first()

    override suspend fun embed(
        texts: List<String>,
        applicationId: String,
        model: String?,
        vendorOptions: EmbeddingVendorOptions?,
    ): List<FloatArray> = embedForResponse(texts, applicationId, model, vendorOptions).results.map { it.output }

    override suspend fun embedForResponse(
        texts: List<String>,
        applicationId: String,
        model: String?,
        vendorOptions: EmbeddingVendorOptions?,
    ): EmbeddingResponse {
        val vendor = getVendor()
        val modelName = model ?: "default"
        val apiKey = apiKeyRepository.findByApplicationIdAndVendorAndDeletedAtIsNull(applicationId, vendor)
            ?: throw EmbeddingServiceException(
                ApiErrorCode.EMBEDDING_API_KEY_NOT_FOUND,
                "applicationId=$applicationId, vendor=$vendor 에 대한 Embedding API 키를 찾을 수 없습니다."
            )
        logger().info("Using API key for embedding - applicationId: $applicationId, vendor: $vendor, apiKeyId: ${apiKey.id}, model: $modelName")
        val embeddingModel: EmbeddingModel = generateEmbeddingModel(apiKey, model)
        val embeddingOptions = generateEmbeddingOptions(model, vendorOptions)
        return if (embeddingOptions != null) {
            embeddingModel.call(EmbeddingRequest(texts, embeddingOptions))
        } else {
            embeddingModel.embedForResponse(texts)
        }
    }
}
