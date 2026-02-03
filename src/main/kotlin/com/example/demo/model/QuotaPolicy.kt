package com.example.demo.model

/**
 * 클라이언트의 쿼터 정책 타입
 * 가격 기반과 토큰 기반 쿼터의 조합을 나타냄
 */
enum class QuotaPolicy {
    /**
     * 가격 기반 쿼터만 설정됨
     */
    PRICING_ONLY,
    
    /**
     * 토큰 기반 쿼터만 설정됨
     */
    TOKEN_ONLY,
    
    /**
     * 가격 기반과 토큰 기반 쿼터 모두 설정됨
     */
    BOTH,
    
    /**
     * 쿼터 제한 없음
     */
    NONE;
    
    companion object {
        /**
         * 쿼터 존재 여부로부터 정책 결정
         * @param hasPricingQuota 가격 기반 쿼터 존재 여부
         * @param hasTokenQuota 토큰 기반 쿼터 존재 여부
         * @return 결정된 쿼터 정책
         */
        fun fromQuotas(
            hasPricingQuota: Boolean,
            hasTokenQuota: Boolean
        ): QuotaPolicy {
            return when {
                hasPricingQuota && hasTokenQuota -> BOTH
                hasPricingQuota -> PRICING_ONLY
                hasTokenQuota -> TOKEN_ONLY
                else -> NONE
            }
        }
    }
}
