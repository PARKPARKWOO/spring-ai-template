package com.example.demo.model

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * SaaS 방식의 AppKey (API Key) 엔티티
 * 키는 해시로 저장되며, 원본 키는 발급 시 한 번만 반환됩니다.
 */
@Entity
@Table(
    name = "app_key",
    indexes = [
        Index(name = "idx_app_key_client_id", columnList = "client_id"),
        Index(name = "idx_app_key_hash", columnList = "key_hash")
    ]
)
class AppKey(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    val client: Client,

    @Column(name = "key_hash", nullable = false, unique = true, length = 255)
    val keyHash: String,

    @Column(nullable = false, length = 100)
    val name: String,

    @Column(columnDefinition = "TEXT")
    val description: String? = null,

    @Column(name = "expires_at")
    val expiresAt: LocalDateTime? = null, // null이면 무제한

    @Column(name = "is_active", nullable = false)
    val isActive: Boolean = true,

    @Column(name = "last_used_at")
    val lastUsedAt: LocalDateTime? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime,

    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime,

    @Column(name = "deleted_at")
    val deletedAt: LocalDateTime? = null,
) {
    /**
     * 키가 만료되었는지 확인
     */
    fun isExpired(): Boolean {
        val expiresAtValue = expiresAt
        return expiresAtValue != null && expiresAtValue.isBefore(LocalDateTime.now())
    }

    /**
     * 키가 사용 가능한지 확인 (활성화되어 있고, 삭제되지 않았고, 만료되지 않음)
     */
    fun isUsable(): Boolean {
        return isActive && deletedAt == null && !isExpired()
    }
}
