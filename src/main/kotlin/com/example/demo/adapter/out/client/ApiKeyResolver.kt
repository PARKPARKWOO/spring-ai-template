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
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicLong

/**
 * API Key 선택 전략.
 *
 * 1) applicationId 전용 키 (PAID → FREE)
 * 2) 공용 키 풀 (application_id IS NULL, PAID → FREE)
 * 3) Rate limit이 남아있는 키만 선택 (round-robin + 스킵)
 * 4) 모든 키가 한도 초과면 AI_QUOTA_EXCEEDED 예외
 */
@Component
class ApiKeyResolver(
    private val apiKeyRepository: ApiKeyRepository,
    private val rateLimiter: ApiKeyRateLimiter,
) {
    private val log = LoggerFactory.getLogger(ApiKeyResolver::class.java)
    private val counters = ConcurrentHashMap<String, AtomicLong>()

    companion object {
        private const val SHARED_POOL = "shared"
    }

    fun resolve(applicationId: String, vendor: Vendor): ApiKey {
        // 1) applicationId 전용 키 (PAID → FREE)
        resolveFromDedicated(applicationId, vendor)?.let { return it }

        // 2) 공용 키 풀 (PAID → FREE)
        resolveFromSharedPool(vendor)?.let { return it }

        throw AiServiceException(
            ApiErrorCode.AI_API_KEY_NOT_FOUND,
            "vendor=$vendor 에 대한 API 키를 찾을 수 없습니다. (전용/공용 모두 없음)"
        )
    }

    fun resolve(applicationId: String, vendor: Vendor, tier: ApiKeyTier): ApiKey {
        // 전용 키 시도
        val dedicatedKeys = apiKeyRepository.findByApplicationIdAndVendorAndTierAndDeletedAtIsNullOrderByIdAsc(
            applicationId, vendor, tier,
        )
        if (dedicatedKeys.isNotEmpty()) {
            selectAvailable(dedicatedKeys, applicationId, vendor, tier)?.let { return it }
        }

        // 공용 키 풀 시도
        val sharedKeys = apiKeyRepository.findByApplicationIdIsNullAndVendorAndTierAndDeletedAtIsNullOrderByIdAsc(
            vendor, tier,
        )
        if (sharedKeys.isNotEmpty()) {
            selectAvailable(sharedKeys, SHARED_POOL, vendor, tier)?.let { return it }
        }

        if (dedicatedKeys.isNotEmpty() || sharedKeys.isNotEmpty()) {
            throw AiServiceException(
                ApiErrorCode.AI_QUOTA_EXCEEDED,
                "vendor=$vendor, tier=$tier 의 모든 API 키가 rate limit에 도달했습니다."
            )
        }

        throw AiServiceException(
            ApiErrorCode.AI_API_KEY_NOT_FOUND,
            "vendor=$vendor, tier=$tier 에 대한 API 키를 찾을 수 없습니다."
        )
    }

    private fun resolveFromDedicated(applicationId: String, vendor: Vendor): ApiKey? {
        val paidKeys = apiKeyRepository.findByApplicationIdAndVendorAndTierAndDeletedAtIsNullOrderByIdAsc(
            applicationId, vendor, ApiKeyTier.PAID,
        )
        if (paidKeys.isNotEmpty()) {
            selectAvailable(paidKeys, applicationId, vendor, ApiKeyTier.PAID)?.let { return it }
        }

        val freeKeys = apiKeyRepository.findByApplicationIdAndVendorAndTierAndDeletedAtIsNullOrderByIdAsc(
            applicationId, vendor, ApiKeyTier.FREE,
        )
        if (freeKeys.isNotEmpty()) {
            selectAvailable(freeKeys, applicationId, vendor, ApiKeyTier.FREE)?.let { return it }
        }

        return null
    }

    private fun resolveFromSharedPool(vendor: Vendor): ApiKey? {
        val paidKeys = apiKeyRepository.findByApplicationIdIsNullAndVendorAndTierAndDeletedAtIsNullOrderByIdAsc(
            vendor, ApiKeyTier.PAID,
        )
        if (paidKeys.isNotEmpty()) {
            selectAvailable(paidKeys, SHARED_POOL, vendor, ApiKeyTier.PAID)?.let { return it }
        }

        val freeKeys = apiKeyRepository.findByApplicationIdIsNullAndVendorAndTierAndDeletedAtIsNullOrderByIdAsc(
            vendor, ApiKeyTier.FREE,
        )
        if (freeKeys.isNotEmpty()) {
            selectAvailable(freeKeys, SHARED_POOL, vendor, ApiKeyTier.FREE)?.let { return it }
            throw AiServiceException(
                ApiErrorCode.AI_QUOTA_EXCEEDED,
                "vendor=$vendor 의 공용 키 풀 전체가 rate limit에 도달했습니다."
            )
        }

        return null
    }

    /**
     * Round-robin으로 순회하면서 rate limit이 남아있는 첫 번째 키를 반환.
     * 모든 키가 한도 초과면 null.
     */
    private fun selectAvailable(
        keys: List<ApiKey>,
        poolKey: String,
        vendor: Vendor,
        tier: ApiKeyTier,
    ): ApiKey? {
        val counterKey = "$poolKey:$vendor:$tier"
        // 인스턴스 부팅/재시작 시 매번 keys[0] 편중 방지 — 첫 호출 시 random 시작점
        val counter = counters.computeIfAbsent(counterKey) {
            AtomicLong(ThreadLocalRandom.current().nextLong(0, Int.MAX_VALUE.toLong()))
        }
        val startIndex = counter.getAndIncrement()

        for (i in keys.indices) {
            val index = ((startIndex + i) % keys.size).toInt()
            val candidate = keys[index]
            if (rateLimiter.isAvailable(candidate.id, vendor, tier, candidate.applicationId)) {
                log.debug(
                    "API key selected - pool: {}, vendor: {}, tier: {}, keyId: {}, index: {}/{}",
                    poolKey, vendor, tier, candidate.id, index, keys.size,
                )
                return candidate
            }
            log.debug("API key {} skipped (rate limited)", candidate.id)
        }
        return null
    }
}
