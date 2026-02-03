package com.example.demo.adapter.out.client

import com.example.demo.adapter.out.persistence.ApiKeyRepository
import com.example.demo.business.TokenizerService
import com.example.demo.business.exception.EmbeddingServiceException
import com.example.demo.dto.EmbeddingVendorOptions
import com.example.demo.model.ApiErrorCode
import com.example.demo.model.Vendor
import com.example.demo.model.api.ApiKey
import com.example.demo.model.api.GoogleApiKey
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.embedding.EmbeddingOptions
import org.springframework.ai.google.genai.GoogleGenAiEmbeddingConnectionDetails
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingModel
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions
import org.springframework.stereotype.Component

@Component
class GeminiEmbeddingClient(
    apiKeyRepository: ApiKeyRepository,
    tokenizerService: TokenizerService,
) : AbstractEmbeddingTemplate(apiKeyRepository, tokenizerService) {

    override fun getVendor(): Vendor = Vendor.GOOGLE

    override fun generateEmbeddingModel(apiKey: ApiKey, model: String?): EmbeddingModel {
        val googleApiKey = apiKey as? GoogleApiKey
            ?: throw EmbeddingServiceException(
                ApiErrorCode.EMBEDDING_API_KEY_NOT_FOUND,
                "Google 벤더에 대한 유효한 Embedding API 키 타입이 아닙니다. (API Key ID: ${apiKey.id})"
            )

        val connectionDetails = GoogleGenAiEmbeddingConnectionDetails.builder()
            .apiKey(googleApiKey.apiKey)
            .build()

        // 기본 옵션으로 모델 생성 (런타임 옵션은 generateEmbeddingOptions에서 처리)
        val defaultOptions = GoogleGenAiTextEmbeddingOptions.builder()
            .model(model ?: GoogleGenAiTextEmbeddingOptions.DEFAULT_MODEL_NAME)
            .taskType(GoogleGenAiTextEmbeddingOptions.TaskType.RETRIEVAL_DOCUMENT)
            .build()

        return GoogleGenAiTextEmbeddingModel(connectionDetails, defaultOptions)
    }

    override fun generateEmbeddingOptions(
        model: String?,
        vendorOptions: EmbeddingVendorOptions?,
    ): EmbeddingOptions? {
        val options = GoogleGenAiTextEmbeddingOptions.builder()

        // 모델 설정
        model?.let { options.model(it) }

        // 벤더별 옵션 적용
        if (vendorOptions is EmbeddingVendorOptions.GeminiOptions) {
            vendorOptions.taskType?.let { taskTypeStr ->
                val taskType = try {
                    GoogleGenAiTextEmbeddingOptions.TaskType.valueOf(taskTypeStr)
                } catch (e: IllegalArgumentException) {
                    null
                }
                taskType?.let { options.taskType(it) }
            }
            vendorOptions.dimensions?.let { options.dimensions(it) }
            vendorOptions.title?.let { options.title(it) }
            vendorOptions.autoTruncate?.let { options.autoTruncate(it) }
        }

        return options.build()
    }
}
