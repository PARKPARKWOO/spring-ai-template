package com.example.demo.common.ratelimit

import com.example.demo.model.ApiKeyTier
import com.example.demo.model.Vendor
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * API Key별 Rate Limit 추적기.
 *
 * - RPM (Requests Per Minute): 고정 윈도우 방식, 매분 리셋
 * - RPD (Requests Per Day): 자정 PST 기준 리셋
 * - 키별로 독립 추적, 한도 초과 시 해당 키를 건너뜀
 *
 * 정책 값은 [RateLimitPolicyService] 에서 resolve (DB → 공용 default → 하드코딩 fallback).
 */
@Component
class ApiKeyRateLimiter(
    private val policyService: RateLimitPolicyService,
) {
    private val log = LoggerFactory.getLogger(ApiKeyRateLimiter::class.java)
    private val states = ConcurrentHashMap<Long, KeyRateState>()

    companion object {
        private val PST = ZoneId.of("America/Los_Angeles")
    }

    fun isAvailable(
        keyId: Long,
        vendor: Vendor,
        tier: ApiKeyTier,
        applicationId: String? = null,
    ): Boolean {
        val limit = policyService.resolve(vendor, tier, applicationId)
        val state = getOrCreate(keyId)
        state.resetIfNeeded()
        return state.rpm.get() < limit.rpm && state.rpd.get() < limit.rpd
    }

    fun record(keyId: Long) {
        val state = getOrCreate(keyId)
        state.resetIfNeeded()
        state.rpm.incrementAndGet()
        state.rpd.incrementAndGet()
    }

    fun markRateLimited(
        keyId: Long,
        vendor: Vendor,
        tier: ApiKeyTier,
        applicationId: String? = null,
    ) {
        val limit = policyService.resolve(vendor, tier, applicationId)
        val state = getOrCreate(keyId)
        state.rpm.set(limit.rpm)
        log.warn("API key {} marked as rate-limited for current minute", keyId)
    }

    fun getUsage(keyId: Long): Pair<Int, Int> {
        val state = states[keyId] ?: return 0 to 0
        state.resetIfNeeded()
        return state.rpm.get() to state.rpd.get()
    }

    private fun getOrCreate(keyId: Long): KeyRateState =
        states.computeIfAbsent(keyId) { KeyRateState() }

    private class KeyRateState {
        val rpm = AtomicInteger(0)
        val rpd = AtomicInteger(0)
        val rpmResetAt = AtomicLong(nextMinuteBoundary())
        val rpdResetAt = AtomicLong(nextMidnightPst())

        fun resetIfNeeded() {
            val now = System.currentTimeMillis()
            if (now >= rpmResetAt.get()) {
                rpm.set(0)
                rpmResetAt.set(nextMinuteBoundary())
            }
            if (now >= rpdResetAt.get()) {
                rpd.set(0)
                rpdResetAt.set(nextMidnightPst())
            }
        }

        private fun nextMinuteBoundary(): Long {
            val now = Instant.now()
            return now.plusSeconds(60 - (now.epochSecond % 60)).toEpochMilli()
        }

        private fun nextMidnightPst(): Long {
            val nowPst = ZonedDateTime.now(PST)
            val midnight = nowPst.toLocalDate().plusDays(1).atStartOfDay(PST)
            return midnight.toInstant().toEpochMilli()
        }
    }
}
