package com.example.demo.adapter.out.client

import com.example.demo.adapter.out.persistence.ApiKeyRepository
import com.example.demo.business.exception.AiServiceException
import com.example.demo.common.ratelimit.ApiKeyRateLimiter
import com.example.demo.model.ApiErrorCode
import com.example.demo.model.ApiKeyTier
import com.example.demo.model.Vendor
import com.example.demo.model.api.ApiKey
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * API Key 선택 전략.
 *
 * - PAID 키 우선, 없으면 FREE 키 사용
 * - Rate limit이 남아있는 키만 선택 (round-robin + 스킵)
 * - 모든 키가 한도 초과면 AI_QUOTA_EXCEEDED 예외
 */
@Component
class ApiKeyResolver(
    private val apiKeyRepository: ApiKeyRepository,
    private val rateLimiter: ApiKeyRateLimiter,
) {
    private val log = LoggerFactory.getLogger(ApiKeyResolver::class.java)
    private val counters = ConcurrentHashMap<String, AtomicLong>()

    fun resolve(applicationId: String, vendor: Vendor): ApiKey {
        // PAID 키 우선
        val paidKeys = apiKeyRepository.findByApplicationIdAndVendorAndTierAndDeletedAtIsNull(
            applicationId, vendor, ApiKeyTier.PAID,
        )
        if (paidKeys.isNotEmpty()) {
            val selected = selectAvailable(paidKeys, applicationId, vendor, ApiKeyTier.PAID)
            if (selected != null) return selected
        }

        // FREE 키
        val freeKeys = apiKeyRepository.findByApplicationIdAndVendorAndTierAndDeletedAtIsNull(
            applicationId, vendor, ApiKeyTier.FREE,
        )
        if (freeKeys.isNotEmpty()) {
            val selected = selectAvailable(freeKeys, applicationId, vendor, ApiKeyTier.FREE)
            if (selected != null) return selected
            // 모든 FREE 키 한도 초과
            throw AiServiceException(
                ApiErrorCode.AI_QUOTA_EXCEEDED,
                "applicationId=$applicationId, vendor=$vendor 의 모든 FREE API 키가 rate limit에 도달했습니다."
            )
        }

        throw AiServiceException(
            ApiErrorCode.AI_API_KEY_NOT_FOUND,
            "applicationId=$applicationId, vendor=$vendor 에 대한 API 키를 찾을 수 없습니다."
        )
    }

    fun resolve(applicationId: String, vendor: Vendor, tier: ApiKeyTier): ApiKey {
        val keys = apiKeyRepository.findByApplicationIdAndVendorAndTierAndDeletedAtIsNull(
            applicationId, vendor, tier,
        )
        if (keys.isEmpty()) {
            throw AiServiceException(
                ApiErrorCode.AI_API_KEY_NOT_FOUND,
                "applicationId=$applicationId, vendor=$vendor, tier=$tier 에 대한 API 키를 찾을 수 없습니다."
            )
        }
        return selectAvailable(keys, applicationId, vendor, tier)
            ?: throw AiServiceException(
                ApiErrorCode.AI_QUOTA_EXCEEDED,
                "applicationId=$applicationId, vendor=$vendor, tier=$tier 의 모든 API 키가 rate limit에 도달했습니다."
            )
    }

    /**
     * Round-robin으로 순회하면서 rate limit이 남아있는 첫 번째 키를 반환.
     * 모든 키가 한도 초과면 null.
     */
    private fun selectAvailable(
        keys: List<ApiKey>,
        applicationId: String,
        vendor: Vendor,
        tier: ApiKeyTier,
    ): ApiKey? {
        val counterKey = "$applicationId:$vendor:$tier"
        val counter = counters.computeIfAbsent(counterKey) { AtomicLong(0) }
        val startIndex = counter.getAndIncrement()

        for (i in keys.indices) {
            val index = ((startIndex + i) % keys.size).toInt()
            val candidate = keys[index]
            if (rateLimiter.isAvailable(candidate.id, vendor, tier)) {
                log.debug(
                    "API key selected - app: {}, vendor: {}, tier: {}, keyId: {}, index: {}/{}",
                    applicationId, vendor, tier, candidate.id, index, keys.size,
                )
                return candidate
            }
            log.debug("API key {} skipped (rate limited)", candidate.id)
        }
        return null
    }
}
