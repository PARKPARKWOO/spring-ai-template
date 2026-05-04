package com.example.demo.common.ratelimit

/**
 * 429 응답의 retry-after 정보를 cause exception 메시지에서 best-effort 로 파싱.
 *
 * Spring AI 가 직접 retry-after 헤더를 노출하지 않아서 메시지 텍스트에서 추출.
 * 못 찾으면 null 반환 → 호출자가 [ApiKeyRateLimiter.DEFAULT_COOLDOWN_MS] 사용.
 *
 * 파싱 패턴 (운영에서 관찰된 형태):
 *  - Gemini: "Please retry in 30s" / "retry in 1m30s" / "retryDelay: \"30s\""
 *  - Anthropic: "rate limit ... retry-after: 60"
 *  - Generic: "retry after 60 seconds"
 */
object RetryAfterParser {

    /** "retry in 30s" 또는 "retry-after: 30" 같은 패턴에서 초 단위 추출 */
    private val SECONDS_PATTERN = Regex(
        """retry[\s\-_]*(?:in|after)?[:\s]*(\d+)\s*s""",
        RegexOption.IGNORE_CASE,
    )

    /** Gemini 의 protobuf retryDelay 형식: "retryDelay: \"30s\"" */
    private val RETRY_DELAY_PATTERN = Regex(
        """retryDelay[:\s]*"?(\d+)s"?""",
        RegexOption.IGNORE_CASE,
    )

    /** "retry in 1m30s" / "retry in 2 minutes" — 분 단위 */
    private val MINUTES_PATTERN = Regex(
        """retry[\s\-_]*(?:in|after)?[:\s]*(\d+)\s*(?:m|min|minutes?)\b""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * @return cooldown ms (초 단위 → ms 변환). 못 찾으면 null.
     */
    fun parse(message: String?): Long? {
        if (message.isNullOrBlank()) return null

        RETRY_DELAY_PATTERN.find(message)?.let {
            return it.groupValues[1].toLongOrNull()?.let { s -> s * 1000L }
        }
        SECONDS_PATTERN.find(message)?.let {
            return it.groupValues[1].toLongOrNull()?.let { s -> s * 1000L }
        }
        MINUTES_PATTERN.find(message)?.let {
            return it.groupValues[1].toLongOrNull()?.let { m -> m * 60_000L }
        }
        return null
    }
}
