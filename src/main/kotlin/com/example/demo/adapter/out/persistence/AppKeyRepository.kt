package com.example.demo.adapter.out.persistence

import com.example.demo.model.AppKey
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface AppKeyRepository : JpaRepository<AppKey, Long> {
    /**
     * 해시된 키로 AppKey 조회
     */
    @Query("""
        SELECT ak FROM AppKey ak
        WHERE ak.keyHash = :keyHash
        AND ak.deletedAt IS NULL
    """)
    fun findByKeyHash(@Param("keyHash") keyHash: String): AppKey?

    /**
     * 클라이언트 ID로 활성화된 AppKey 목록 조회
     */
    @Query("""
        SELECT ak FROM AppKey ak
        WHERE ak.client.id = :clientId
        AND ak.deletedAt IS NULL
        ORDER BY ak.createdAt DESC
    """)
    fun findByClientId(@Param("clientId") clientId: Long): List<AppKey>

    /**
     * 클라이언트 ID와 AppKey ID로 조회 (소유권 확인용)
     */
    @Query("""
        SELECT ak FROM AppKey ak
        WHERE ak.id = :appKeyId
        AND ak.client.id = :clientId
        AND ak.deletedAt IS NULL
    """)
    fun findByIdAndClientId(
        @Param("appKeyId") appKeyId: Long,
        @Param("clientId") clientId: Long
    ): AppKey?

    /**
     * 모든 활성화된 AppKey를 client와 함께 조회 (fetch join)
     */
    @Query("""
        SELECT ak FROM AppKey ak
        JOIN FETCH ak.client c
        WHERE ak.deletedAt IS NULL
        AND ak.isActive = true
    """)
    fun findAllActiveWithClient(): List<AppKey>
}
