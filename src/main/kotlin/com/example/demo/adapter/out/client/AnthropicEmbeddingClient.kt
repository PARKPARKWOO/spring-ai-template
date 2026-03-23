package com.example.demo.adapter.out.client

import com.example.demo.business.TokenizerService
import com.example.demo.common.ratelimit.ApiKeyRateLimiter
import com.example.demo.business.exception.EmbeddingServiceException
import com.example.demo.dto.EmbeddingVendorOptions
import com.example.demo.model.ApiErrorCode
import com.example.demo.model.Vendor
import com.example.demo.model.api.AnthropicApiKey
import com.example.demo.model.api.ApiKey
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.embedding.EmbeddingOptions
import org.springframework.stereotype.Component

@Component
class AnthropicEmbeddingClient(
    apiKeyResolver: ApiKeyResolver,
    tokenizerService: TokenizerService,
    rateLimiter: ApiKeyRateLimiter,
) : AbstractEmbeddingTemplate(apiKeyResolver, tokenizerService, rateLimiter) {

    override fun getVendor(): Vendor = Vendor.ANTHROPIC

    override fun generateEmbeddingModel(apiKey: ApiKey, model: String?): EmbeddingModel {
        val anthropicApiKey = apiKey as? AnthropicApiKey
            ?: throw EmbeddingServiceException(
                ApiErrorCode.EMBEDDING_API_KEY_NOT_FOUND,
                "Anthropic 벤더에 대한 유효한 Embedding API 키 타입이 아닙니다. (API Key ID: ${apiKey.id})"
            )

        // TODO: Anthropic Claude Embedding 모델 구현
        // Anthropic은 현재 Embedding API를 제공하지 않을 수 있음
        throw EmbeddingServiceException(
            ApiErrorCode.EMBEDDING_VENDOR_NOT_SUPPORTED,
            "Anthropic 벤더는 현재 Embedding API를 지원하지 않습니다."
        )
    }

    override fun generateEmbeddingOptions(
        model: String?,
        vendorOptions: EmbeddingVendorOptions?,
    ): EmbeddingOptions? {
        // Anthropic은 Embedding을 지원하지 않으므로 null 반환
        return null
    }
}
