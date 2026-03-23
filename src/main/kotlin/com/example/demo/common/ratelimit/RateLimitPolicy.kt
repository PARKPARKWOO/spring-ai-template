package com.example.demo.common.ratelimit

import com.example.demo.model.ApiKeyTier
import com.example.demo.model.Vendor

/**
 * 벤더/티어별 Rate Limit 정책.
 * Gemini free tier 기준: https://ai.google.dev/gemini-api/docs/rate-limits
 */
data class RateLimitPolicy(
    val rpm: Int,
    val rpd: Int,
) {
    companion object {
        // Gemini Free tier
        private val GOOGLE_FREE = RateLimitPolicy(rpm = 10, rpd = 250)
        // Gemini Paid tier (Tier 1 기준)
        private val GOOGLE_PAID = RateLimitPolicy(rpm = 300, rpd = 10_000)

        // OpenAI (Tier 1 기준, free tier 없음)
        private val OPENAI_FREE = RateLimitPolicy(rpm = 3, rpd = 200)
        private val OPENAI_PAID = RateLimitPolicy(rpm = 500, rpd = 50_000)

        // Anthropic
        private val ANTHROPIC_FREE = RateLimitPolicy(rpm = 5, rpd = 100)
        private val ANTHROPIC_PAID = RateLimitPolicy(rpm = 50, rpd = 10_000)

        // xAI (Grok)
        private val XAI_FREE = RateLimitPolicy(rpm = 5, rpd = 100)
        private val XAI_PAID = RateLimitPolicy(rpm = 60, rpd = 10_000)

        // 제한 없음 (fallback)
        private val UNLIMITED = RateLimitPolicy(rpm = Int.MAX_VALUE, rpd = Int.MAX_VALUE)

        fun of(vendor: Vendor, tier: ApiKeyTier): RateLimitPolicy = when (vendor) {
            Vendor.GOOGLE -> if (tier == ApiKeyTier.FREE) GOOGLE_FREE else GOOGLE_PAID
            Vendor.OPENAI -> if (tier == ApiKeyTier.FREE) OPENAI_FREE else OPENAI_PAID
            Vendor.ANTHROPIC -> if (tier == ApiKeyTier.FREE) ANTHROPIC_FREE else ANTHROPIC_PAID
            Vendor.X_AI -> if (tier == ApiKeyTier.FREE) XAI_FREE else XAI_PAID
        }
    }
}
