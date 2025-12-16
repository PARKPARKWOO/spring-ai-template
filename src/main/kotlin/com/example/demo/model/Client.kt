package com.example.demo.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
class Client(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    val name: String,
    
    @Column(nullable = false)
    val password: String,
    
    val description: String? = null,
    
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime,
    
    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime,
    
    @Column(name = "deleted_at")
    val deletedAt: LocalDateTime? = null,
) {
    @OneToMany(mappedBy = "client", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val clientApiKeys: List<ClientApiKey> = emptyList()
}

