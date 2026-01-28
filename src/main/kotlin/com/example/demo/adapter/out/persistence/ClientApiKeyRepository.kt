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
    
    /**
     * Client ID로 연결된 ApiKey 목록 조회 (fetch join)
     */
    @Query("""
        SELECT cak FROM ClientApiKey cak
        JOIN FETCH cak.apiKey ak
        JOIN FETCH cak.client c
        WHERE cak.client.id = :clientId
        AND cak.deletedAt IS NULL
        AND ak.deletedAt IS NULL
        ORDER BY cak.createdAt DESC
    """)
    fun findByClientIdWithApiKey(@Param("clientId") clientId: Long): List<ClientApiKey>
    
    /**
     * ApiKey ID로 연결된 Client 목록 조회 (fetch join)
     */
    @Query("""
        SELECT cak FROM ClientApiKey cak
        JOIN FETCH cak.client c
        JOIN FETCH cak.apiKey ak
        WHERE cak.apiKey.id = :apiKeyId
        AND cak.deletedAt IS NULL
        AND c.deletedAt IS NULL
        ORDER BY cak.createdAt DESC
    """)
    fun findByApiKeyIdWithClient(@Param("apiKeyId") apiKeyId: Long): List<ClientApiKey>
}

