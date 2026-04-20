package com.example.demo.business

import com.example.demo.adapter.out.persistence.ApiKeyRepository
import com.example.demo.business.exception.AiServiceException
import com.example.demo.model.ApiErrorCode
import com.example.demo.model.ApiKeyTier
import com.example.demo.model.Vendor
import com.example.demo.model.api.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class ApiKeyService(
    private val apiKeyRepository: ApiKeyRepository,
) {
    @Transactional
    fun register(
        applicationId: String? = null,
        vendor: Vendor,
        tier: ApiKeyTier,
        apiKey: String,
        description: String,
        projectId: String? = null,
        location: String? = null,
    ): ApiKey {
        val now = LocalDateTime.now()
        val entity = when (vendor) {
            Vendor.OPENAI -> OpenAiApiKey(
                applicationId = applicationId,
                tier = tier,
                description = description,
                apiKey = apiKey,
                createdAt = now,
                updatedAt = now,
            )
            Vendor.ANTHROPIC -> AnthropicApiKey(
                applicationId = applicationId,
                tier = tier,
                description = description,
                apiKey = apiKey,
                createdAt = now,
                updatedAt = now,
            )
            Vendor.GOOGLE -> GoogleApiKey(
                applicationId = applicationId,
                tier = tier,
                description = description,
                apiKey = apiKey,
                projectId = projectId,
                location = location,
                createdAt = now,
                updatedAt = now,
            )
            Vendor.X_AI -> XAiApiKey(
                applicationId = applicationId,
                tier = tier,
                description = description,
                apiKey = apiKey,
                createdAt = now,
                updatedAt = now,
            )
        }
        return apiKeyRepository.save(entity)
    }

    fun list(
        applicationId: String? = null,
        vendor: Vendor? = null,
        tier: ApiKeyTier? = null,
    ): List<ApiKey> {
        if (applicationId == null) {
            // 전체 조회 (공용 풀 + 모든 application 키). in-memory vendor/tier 필터.
            return apiKeyRepository.findAllByDeletedAtIsNull()
                .filter { vendor == null || it.vendor == vendor }
                .filter { tier == null || it.tier == tier }
        }
        if (vendor != null && tier != null) {
            return apiKeyRepository.findByApplicationIdAndVendorAndTierAndDeletedAtIsNull(applicationId, vendor, tier)
        }
        if (vendor != null) {
            return apiKeyRepository.findByApplicationIdAndVendorAndDeletedAtIsNull(applicationId, vendor)
        }
        return apiKeyRepository.findByApplicationIdAndDeletedAtIsNull(applicationId)
    }

    @Transactional
    fun delete(id: Long) {
        val apiKey = apiKeyRepository.findById(id).orElseThrow {
            AiServiceException(ApiErrorCode.AI_API_KEY_NOT_FOUND, "API Key를 찾을 수 없습니다. id=$id")
        }
        apiKeyRepository.delete(apiKey)
    }
}
