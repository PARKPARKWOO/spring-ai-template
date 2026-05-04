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
 * - **Cooldown** (429 명시 차단): 벤더 측 quota exhaust 발생 시 일정 시간 키 차단.
 *   RPM 한도와는 별개라 분 boundary 가 와도 자동 해제 안 됨 (벤더 quota 가 분 단위가 아닐 수 있음).
 * - 키별로 독립 추적, 한도 초과 또는 cooldown 중이면 해당 키를 건너뜀
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

        /** 429 발생 시 명시적으로 키를 차단하는 기본 시간. retry-after 헤더 추출 실패 시 사용. */
        const val DEFAULT_COOLDOWN_MS: Long = 60_000L

        /** retry-after 파싱 결과의 최소/최대 — 노이즈 방어 */
        const val MIN_COOLDOWN_MS: Long = 5_000L
        const val MAX_COOLDOWN_MS: Long = 15 * 60 * 1000L  // 15분
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
        if (System.currentTimeMillis() < state.blockedUntilMs.get()) return false
        return state.rpm.get() < limit.rpm && state.rpd.get() < limit.rpd
    }

    fun record(keyId: Long) {
        val state = getOrCreate(keyId)
        state.resetIfNeeded()
        state.rpm.incrementAndGet()
        state.rpd.incrementAndGet()
    }

    /**
     * 429 발생 시 키를 [cooldownMs] 동안 차단. RPM 카운터도 한도까지 채워 분 boundary 까지 어차피 막힘.
     * cooldownMs 가 분 boundary 보다 길면 cooldown 이 더 강함 (벤더 quota 가 분 단위가 아닌 케이스 대비).
     */
    fun markRateLimited(
        keyId: Long,
        vendor: Vendor,
        tier: ApiKeyTier,
        applicationId: String? = null,
        cooldownMs: Long = DEFAULT_COOLDOWN_MS,
    ) {
        val limit = policyService.resolve(vendor, tier, applicationId)
        val state = getOrCreate(keyId)
        state.rpm.set(limit.rpm)

        val clamped = cooldownMs.coerceIn(MIN_COOLDOWN_MS, MAX_COOLDOWN_MS)
        val blockedUntil = System.currentTimeMillis() + clamped
        state.blockedUntilMs.updateAndGet { current -> maxOf(current, blockedUntil) }

        log.warn(
            "API key {} (vendor={}, tier={}) marked rate-limited: cooldown={}ms, blockedUntil={}",
            keyId, vendor, tier, clamped, blockedUntil,
        )
    }

    fun getUsage(keyId: Long): Pair<Int, Int> {
        val state = states[keyId] ?: return 0 to 0
        state.resetIfNeeded()
        return state.rpm.get() to state.rpd.get()
    }

    /** 디버깅/모니터링용 — 키가 cooldown 중인지 확인 */
    fun cooldownRemainingMs(keyId: Long): Long {
        val state = states[keyId] ?: return 0L
        val remaining = state.blockedUntilMs.get() - System.currentTimeMillis()
        return if (remaining > 0) remaining else 0L
    }

    private fun getOrCreate(keyId: Long): KeyRateState =
        states.computeIfAbsent(keyId) { KeyRateState() }

    private class KeyRateState {
        val rpm = AtomicInteger(0)
        val rpd = AtomicInteger(0)
        val rpmResetAt = AtomicLong(nextMinuteBoundary())
        val rpdResetAt = AtomicLong(nextMidnightPst())
        /** 429 명시 차단 시각 (epoch ms). 0 이면 차단 없음. */
        val blockedUntilMs = AtomicLong(0L)

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
