package com.example.demo.adapter.out.persistence

import com.example.demo.model.ApiKeyTier
import com.example.demo.model.Vendor
import com.example.demo.model.ratelimit.RateLimitPolicyEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface RateLimitPolicyRepository : JpaRepository<RateLimitPolicyEntity, Long> {
    @Query(
        """
        SELECT p FROM RateLimitPolicyEntity p
        WHERE p.vendor = :vendor
          AND p.tier = :tier
          AND p.applicationId = :applicationId
          AND p.deletedAt IS NULL
        """,
    )
    fun findOverride(
        @Param("vendor") vendor: Vendor,
        @Param("tier") tier: ApiKeyTier,
        @Param("applicationId") applicationId: String,
    ): RateLimitPolicyEntity?

    @Query(
        """
        SELECT p FROM RateLimitPolicyEntity p
        WHERE p.vendor = :vendor
          AND p.tier = :tier
          AND p.applicationId IS NULL
          AND p.deletedAt IS NULL
        """,
    )
    fun findDefault(
        @Param("vendor") vendor: Vendor,
        @Param("tier") tier: ApiKeyTier,
    ): RateLimitPolicyEntity?

    fun findAllByDeletedAtIsNull(): List<RateLimitPolicyEntity>
}
