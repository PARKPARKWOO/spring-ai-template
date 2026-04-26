package com.example.demo.model.ratelimit

import com.example.demo.model.ApiKeyTier
import com.example.demo.model.Vendor
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "rate_limit_policy")
class RateLimitPolicyEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    val vendor: Vendor,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    val tier: ApiKeyTier,

    @Column(name = "application_id", length = 64)
    val applicationId: String? = null,

    @Column(nullable = false)
    val rpm: Int,

    @Column(nullable = false)
    val rpd: Int,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime,

    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime,

    @Column(name = "deleted_at")
    val deletedAt: LocalDateTime? = null,
)
