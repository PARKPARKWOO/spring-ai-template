package com.example.demo.model

import com.example.demo.model.api.ApiKey
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "client_api_key",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_client_api_key",
            columnNames = ["client_id", "api_key_id"]
        )
    ]
)
class ClientApiKey(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    val client: Client,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "api_key_id", nullable = false)
    val apiKey: ApiKey,
    
    @Column(name = "is_active", nullable = false)
    val isActive: Boolean = true,
    
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime,
    
    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime,
    
    @Column(name = "deleted_at")
    val deletedAt: LocalDateTime? = null,
)

