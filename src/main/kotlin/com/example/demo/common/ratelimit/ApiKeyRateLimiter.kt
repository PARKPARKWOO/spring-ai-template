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
 */
@Component
class ApiKeyRateLimiter {
    private val log = LoggerFactory.getLogger(ApiKeyRateLimiter::class.java)
    private val states = ConcurrentHashMap<Long, KeyRateState>()

    companion object {
        private val PST = ZoneId.of("America/Los_Angeles")
    }

    /**
     * 요청 가능 여부 확인 (카운트 증가 없음)
     */
    fun isAvailable(keyId: Long, vendor: Vendor, tier: ApiKeyTier): Boolean {
        val limit = RateLimitPolicy.of(vendor, tier)
        val state = getOrCreate(keyId)
        state.resetIfNeeded()
        return state.rpm.get() < limit.rpm && state.rpd.get() < limit.rpd
    }

    /**
     * 요청 기록 (호출 성공 후 카운트 증가)
     */
    fun record(keyId: Long) {
        val state = getOrCreate(keyId)
        state.resetIfNeeded()
        state.rpm.incrementAndGet()
        state.rpd.incrementAndGet()
    }

    /**
     * 429 응답 시 해당 키의 RPM 한도를 현재 값으로 채움 (이번 분에는 사용 차단)
     */
    fun markRateLimited(keyId: Long, vendor: Vendor, tier: ApiKeyTier) {
        val limit = RateLimitPolicy.of(vendor, tier)
        val state = getOrCreate(keyId)
        state.rpm.set(limit.rpm)
        log.warn("API key {} marked as rate-limited for current minute", keyId)
    }

    /**
     * 키별 현재 사용량 조회 (모니터링용)
     */
    fun getUsage(keyId: Long): Pair<Int, Int> {
        val state = states[keyId] ?: return 0 to 0
        state.resetIfNeeded()
        return state.rpm.get() to state.rpd.get()
    }

    private fun getOrCreate(keyId: Long): KeyRateState =
        states.computeIfAbsent(keyId) { KeyRateState() }

    /**
     * 키별 rate state. RPM은 매분, RPD는 자정 PST에 리셋.
     */
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
