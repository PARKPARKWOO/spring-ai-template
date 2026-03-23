package com.example.demo.adapter.out.client

import com.example.demo.business.TokenizerService
import com.example.demo.common.ratelimit.ApiKeyRateLimiter
import org.slf4j.LoggerFactory
import com.example.demo.dto.EmbeddingVendorOptions
import com.example.demo.model.Vendor
import com.example.demo.model.api.ApiKey
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.embedding.EmbeddingOptions
import org.springframework.ai.embedding.EmbeddingRequest
import org.springframework.ai.embedding.EmbeddingResponse

abstract class AbstractEmbeddingTemplate(
    private val apiKeyResolver: ApiKeyResolver,
    private val tokenizerService: TokenizerService,
    private val rateLimiter: ApiKeyRateLimiter,
) : EmbeddingPort {

    private val log = LoggerFactory.getLogger(this::class.java)

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
        val apiKey = apiKeyResolver.resolve(applicationId, vendor)
        log.info("Using API key for embedding - applicationId: $applicationId, vendor: $vendor, apiKeyId: ${apiKey.id}, model: $modelName")
        val embeddingModel: EmbeddingModel = generateEmbeddingModel(apiKey, model)
        val embeddingOptions = generateEmbeddingOptions(model, vendorOptions)
        rateLimiter.record(apiKey.id)
        return if (embeddingOptions != null) {
            embeddingModel.call(EmbeddingRequest(texts, embeddingOptions))
        } else {
            embeddingModel.embedForResponse(texts)
        }
    }
}
