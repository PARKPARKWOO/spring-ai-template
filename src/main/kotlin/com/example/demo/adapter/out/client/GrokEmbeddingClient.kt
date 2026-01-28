package com.example.demo.adapter.out.client

import com.example.demo.adapter.out.persistence.AiUsageLogsRepository
import com.example.demo.adapter.out.persistence.ClientApiKeyRepository
import com.example.demo.business.QuotaManagementService
import com.example.demo.business.TokenizerService
import com.example.demo.business.exception.EmbeddingServiceException
import com.example.demo.dto.EmbeddingVendorOptions
import com.example.demo.model.ApiErrorCode
import com.example.demo.model.Vendor
import com.example.demo.model.api.ApiKey
import com.example.demo.model.api.XAiApiKey
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.embedding.EmbeddingOptions
import org.springframework.ai.openai.OpenAiEmbeddingModel
import org.springframework.ai.openai.OpenAiEmbeddingOptions
import org.springframework.ai.openai.api.OpenAiApi
import org.springframework.stereotype.Component

@Component
class GrokEmbeddingClient(
    clientApiKeyRepository: ClientApiKeyRepository,
    tokenizerService: TokenizerService,
    quotaManagementService: QuotaManagementService,
    aiUsageLogsRepository: AiUsageLogsRepository,
) : AbstractEmbeddingTemplate(
    clientApiKeyRepository,
    tokenizerService,
    quotaManagementService,
    aiUsageLogsRepository,
) {

    override fun getVendor(): Vendor = Vendor.X_AI

    override fun generateEmbeddingModel(apiKey: ApiKey, model: String?): EmbeddingModel {
        val xAiApiKey = apiKey as? XAiApiKey
            ?: throw EmbeddingServiceException(
                ApiErrorCode.EMBEDDING_API_KEY_NOT_FOUND,
                "xAI 벤더에 대한 유효한 Embedding API 키 타입이 아닙니다. (API Key ID: ${apiKey.id})"
            )

        // Grok는 OpenAI API와 호환되므로 OpenAI Embedding 모델 사용
        // xAI 엔드포인트로 설정 필요
        val api = OpenAiApi.builder()
            .apiKey(xAiApiKey.apiKey)
            .baseUrl("https://api.x.ai/v1") // xAI 엔드포인트
            .build()

        return OpenAiEmbeddingModel(api)
    }

    override fun generateEmbeddingOptions(
        model: String?,
        vendorOptions: EmbeddingVendorOptions?,
    ): EmbeddingOptions? {
        val options = OpenAiEmbeddingOptions.builder()

        // 모델 설정
        model?.let { options.model(it) }

        // 벤더별 옵션 적용 (Grok는 OpenAI와 동일한 옵션 사용)
        if (vendorOptions is EmbeddingVendorOptions.GrokOptions) {
            vendorOptions.dimensions?.let { options.dimensions(it) }
            // TODO: encodingFormat 지원 확인 필요 (OpenAiEmbeddingOptions API 확인)
            // vendorOptions.encodingFormat?.let { options.encodingFormat(it) }
            vendorOptions.user?.let { options.user(it) }
        }

        return options.build()
    }
}
