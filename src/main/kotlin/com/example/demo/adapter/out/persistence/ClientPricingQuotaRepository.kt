package com.example.demo.adapter.out.persistence

import com.example.demo.model.ClientPricingQuota
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ClientPricingQuotaRepository : JpaRepository<ClientPricingQuota, Long> {
    /**
     * Client ID로 활성화된 가격 기반 쿼터 조회 (비관적 락)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT q FROM ClientPricingQuota q
        WHERE q.clientId = :clientId
        AND q.isActive = true
        AND q.deletedAt IS NULL
    """)
    fun findByClientIdWithLock(@Param("clientId") clientId: Long): List<ClientPricingQuota>
    
    /**
     * 리셋이 필요한 쿼터 조회
     */
    @Query("""
        SELECT q FROM ClientPricingQuota q
        WHERE q.nextResetAt <= CURRENT_TIMESTAMP
        AND q.isActive = true
        AND q.deletedAt IS NULL
    """)
    fun findQuotasNeedingReset(): List<ClientPricingQuota>
    
    /**
     * Client ID로 활성화된 가격 기반 쿼터 존재 여부 확인
     */
    @Query("""
        SELECT COUNT(q) > 0 FROM ClientPricingQuota q
        WHERE q.clientId = :clientId
        AND q.isActive = true
        AND q.deletedAt IS NULL
    """)
    fun existsByClientIdAndIsActiveTrue(@Param("clientId") clientId: Long): Boolean
}
