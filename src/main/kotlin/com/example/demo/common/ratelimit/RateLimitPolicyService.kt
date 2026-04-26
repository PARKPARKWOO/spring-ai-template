package com.example.demo.common.ratelimit

import com.example.demo.adapter.out.persistence.RateLimitPolicyRepository
import com.example.demo.model.ApiKeyTier
import com.example.demo.model.Vendor
import com.example.demo.model.ratelimit.RateLimitPolicyEntity
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * Rate Limit 정책 resolver.
 *
 * Lookup 순서:
 *   1. (vendor, tier, applicationId) 매치 override
 *   2. (vendor, tier, applicationId IS NULL) 공용 default
 *   3. 코드 하드코딩 값 (`RateLimitPolicy.of()`) — DB 이전 실패 대비 0단계 안전망
 *
 * 캐시: 정책 당 TTL 5분. DB 수정 후 최대 5분 지연 전파. 수동 invalidate 는 [invalidateAll].
 */
@Service
class RateLimitPolicyService(
    private val repository: RateLimitPolicyRepository,
) {
    private val log = LoggerFactory.getLogger(RateLimitPolicyService::class.java)
    private val cache = ConcurrentHashMap<CacheKey, CachedPolicy>()

    companion object {
        private val TTL: Duration = Duration.ofMinutes(5)
    }

    @Transactional(readOnly = true)
    fun resolve(
        vendor: Vendor,
        tier: ApiKeyTier,
        applicationId: String? = null,
    ): RateLimitPolicy {
        val key = CacheKey(vendor, tier, applicationId)
        val now = Instant.now()
        cache[key]?.takeIf { it.expiresAt.isAfter(now) }?.let { return it.policy }

        val policy = lookup(vendor, tier, applicationId)
        cache[key] = CachedPolicy(policy, now.plus(TTL))
        return policy
    }

    @Transactional(readOnly = true)
    fun findAll(): List<RateLimitPolicyEntity> = repository.findAllByDeletedAtIsNull()

    @Transactional
    fun create(
        vendor: Vendor,
        tier: ApiKeyTier,
        applicationId: String?,
        rpm: Int,
        rpd: Int,
    ): RateLimitPolicyEntity {
        require(rpm > 0) { "rpm must be > 0" }
        require(rpd > 0) { "rpd must be > 0" }
        val existing = if (applicationId == null) {
            repository.findDefault(vendor, tier)
        } else {
            repository.findOverride(vendor, tier, applicationId)
        }
        check(existing == null) {
            "rate_limit_policy already exists for (vendor=$vendor, tier=$tier, applicationId=$applicationId)"
        }
        val now = LocalDateTime.now()
        val saved = repository.save(
            RateLimitPolicyEntity(
                vendor = vendor,
                tier = tier,
                applicationId = applicationId,
                rpm = rpm,
                rpd = rpd,
                createdAt = now,
                updatedAt = now,
            ),
        )
        invalidateAll()
        return saved
    }

    @Transactional
    fun update(id: Long, rpm: Int, rpd: Int): RateLimitPolicyEntity {
        require(rpm > 0) { "rpm must be > 0" }
        require(rpd > 0) { "rpd must be > 0" }
        val current = repository.findById(id)
            .orElseThrow { NoSuchElementException("rate_limit_policy not found: id=$id") }
        check(current.deletedAt == null) { "rate_limit_policy id=$id is already deleted" }
        val updated = RateLimitPolicyEntity(
            id = current.id,
            vendor = current.vendor,
            tier = current.tier,
            applicationId = current.applicationId,
            rpm = rpm,
            rpd = rpd,
            createdAt = current.createdAt,
            updatedAt = LocalDateTime.now(),
            deletedAt = null,
        )
        val saved = repository.save(updated)
        invalidateAll()
        return saved
    }

    @Transactional
    fun softDelete(id: Long) {
        val current = repository.findById(id)
            .orElseThrow { NoSuchElementException("rate_limit_policy not found: id=$id") }
        if (current.deletedAt != null) return
        val deleted = RateLimitPolicyEntity(
            id = current.id,
            vendor = current.vendor,
            tier = current.tier,
            applicationId = current.applicationId,
            rpm = current.rpm,
            rpd = current.rpd,
            createdAt = current.createdAt,
            updatedAt = LocalDateTime.now(),
            deletedAt = LocalDateTime.now(),
        )
        repository.save(deleted)
        invalidateAll()
    }

    fun invalidateAll() {
        cache.clear()
    }

    private fun lookup(
        vendor: Vendor,
        tier: ApiKeyTier,
        applicationId: String?,
    ): RateLimitPolicy {
        if (applicationId != null) {
            repository.findOverride(vendor, tier, applicationId)?.let { return it.toPolicy() }
        }
        repository.findDefault(vendor, tier)?.let { return it.toPolicy() }

        log.warn(
            "rate_limit_policy DB miss for vendor={}, tier={}, applicationId={} — falling back to hardcoded",
            vendor,
            tier,
            applicationId,
        )
        return RateLimitPolicy.of(vendor, tier)
    }

    private fun RateLimitPolicyEntity.toPolicy(): RateLimitPolicy = RateLimitPolicy(rpm = rpm, rpd = rpd)

    private data class CacheKey(
        val vendor: Vendor,
        val tier: ApiKeyTier,
        val applicationId: String?,
    )

    private data class CachedPolicy(
        val policy: RateLimitPolicy,
        val expiresAt: Instant,
    )
}
