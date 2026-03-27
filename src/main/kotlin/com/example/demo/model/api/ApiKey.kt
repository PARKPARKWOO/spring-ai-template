package com.example.demo.model.api

import com.example.demo.model.ApiKeyTier
import com.example.demo.model.Vendor
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "api_key")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "vendor_type", discriminatorType = DiscriminatorType.STRING)
abstract class ApiKey(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    /** Application 식별자 (NULL이면 공용 키 풀, 값이 있으면 해당 앱 전용) */
    @Column(name = "application_id", nullable = true, length = 64)
    val applicationId: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val vendor: Vendor,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val tier: ApiKeyTier = ApiKeyTier.FREE,

    val description: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime,

    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime,

    @Column(name = "deleted_at")
    val deletedAt: LocalDateTime? = null,
) {
    abstract fun getApiKeyValue(): String
}