package com.example.demo.adapter.out.persistence

import com.example.demo.model.TokenPricingPolicy
import com.example.demo.model.Vendor
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface TokenPricingPolicyRepository : JpaRepository<TokenPricingPolicy, Long> {
    /**
     * Vendor와 Model로 활성화된 공통 가격 정책 조회 (clientId가 null)
     */
    fun findByVendorAndModelAndClientIdIsNullAndIsActiveTrueAndDeletedAtIsNull(
        vendor: Vendor,
        model: String
    ): TokenPricingPolicy?
    
    /**
     * Client ID, Vendor, Model로 활성화된 Client별 가격 정책 조회
     */
    fun findByClientIdAndVendorAndModelAndIsActiveTrueAndDeletedAtIsNull(
        clientId: Long,
        vendor: Vendor,
        model: String
    ): TokenPricingPolicy?
    
    /**
     * Vendor로 모든 활성화된 공통 가격 정책 조회 (clientId가 null)
     */
    fun findByVendorAndClientIdIsNullAndIsActiveTrueAndDeletedAtIsNull(vendor: Vendor): List<TokenPricingPolicy>
    
    /**
     * Client ID로 모든 활성화된 Client별 가격 정책 조회
     */
    fun findByClientIdAndIsActiveTrueAndDeletedAtIsNull(clientId: Long): List<TokenPricingPolicy>
    
    /**
     * Client ID, Vendor, Model로 정책 조회 (우선순위: Client별 > 공통)
     * Client별 정책이 있으면 반환, 없으면 공통 정책 반환
     */
    @Query("""
        SELECT p FROM TokenPricingPolicy p
        WHERE p.vendor = :vendor
        AND p.model = :model
        AND p.isActive = true
        AND p.deletedAt IS NULL
        AND (p.clientId = :clientId OR p.clientId IS NULL)
        ORDER BY 
            CASE WHEN p.clientId IS NOT NULL THEN 0 ELSE 1 END
        LIMIT 1
    """)
    fun findBestMatchPolicy(
        @Param("clientId") clientId: Long?,
        @Param("vendor") vendor: Vendor,
        @Param("model") model: String
    ): TokenPricingPolicy?
}
