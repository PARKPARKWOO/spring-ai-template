package com.example.demo.adapter.out.persistence

import com.example.demo.model.ClientTokenQuota
import com.example.demo.model.Vendor
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ClientTokenQuotaRepository : JpaRepository<ClientTokenQuota, Long> {
    /**
     * Client ID, Vendor, Model로 활성화된 토큰 기반 쿼터 조회 (비관적 락)
     * 가장 구체적인 쿼터부터 조회 (Model > Vendor > 전체)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT q FROM ClientTokenQuota q
        WHERE q.clientId = :clientId
        AND (q.vendor = :vendor OR q.vendor IS NULL)
        AND (q.model = :model OR q.model IS NULL)
        AND q.isActive = true
        AND q.deletedAt IS NULL
        ORDER BY 
            CASE WHEN q.vendor IS NOT NULL AND q.model IS NOT NULL THEN 1
                 WHEN q.vendor IS NOT NULL THEN 2
                 ELSE 3 END
    """)
    fun findByClientIdAndVendorAndModelWithLock(
        @Param("clientId") clientId: Long,
        @Param("vendor") vendor: Vendor,
        @Param("model") model: String
    ): List<ClientTokenQuota>
    
    /**
     * 리셋이 필요한 쿼터 조회
     */
    @Query("""
        SELECT q FROM ClientTokenQuota q
        WHERE q.nextResetAt <= CURRENT_TIMESTAMP
        AND q.isActive = true
        AND q.deletedAt IS NULL
    """)
    fun findQuotasNeedingReset(): List<ClientTokenQuota>
    
    /**
     * Client ID, Vendor, Model로 활성화된 토큰 기반 쿼터 존재 여부 확인
     */
    @Query("""
        SELECT COUNT(q) > 0 FROM ClientTokenQuota q
        WHERE q.clientId = :clientId
        AND (q.vendor = :vendor OR q.vendor IS NULL)
        AND (q.model = :model OR q.model IS NULL)
        AND q.isActive = true
        AND q.deletedAt IS NULL
    """)
    fun existsByClientIdAndVendorAndModelAndIsActiveTrue(
        @Param("clientId") clientId: Long,
        @Param("vendor") vendor: Vendor,
        @Param("model") model: String
    ): Boolean
}
