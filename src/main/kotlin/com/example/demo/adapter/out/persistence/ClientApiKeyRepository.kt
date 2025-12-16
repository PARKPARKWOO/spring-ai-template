package com.example.demo.adapter.out.persistence

import com.example.demo.model.ClientApiKey
import com.example.demo.model.Vendor
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ClientApiKeyRepository: JpaRepository<ClientApiKey, Long> {
    fun findByClientIdAndApiKeyId(clientId: Long, apiKeyId: Long): ClientApiKey?
    
    @Query("""
        SELECT cak FROM ClientApiKey cak
        JOIN FETCH cak.apiKey ak
        WHERE cak.client.id = :clientId
        AND ak.vendor = :vendor
        AND cak.isActive = true
        AND cak.deletedAt IS NULL
        AND ak.deletedAt IS NULL
        ORDER BY cak.createdAt DESC
    """)
    fun findActiveApiKeyByClientIdAndVendor(
        @Param("clientId") clientId: Long,
        @Param("vendor") vendor: Vendor
    ): ClientApiKey?
}

