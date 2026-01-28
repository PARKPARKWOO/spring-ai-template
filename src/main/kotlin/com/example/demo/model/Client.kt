package com.example.demo.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
class Client(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    val name: String,
    
    @Column(nullable = false)
    var password: String,
    
    val description: String? = null,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val role: ClientRole = ClientRole.CLIENT,
    
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime,
    
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime,
    
    @Column(name = "deleted_at")
    val deletedAt: LocalDateTime? = null,
) {
    @OneToMany(mappedBy = "client", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val clientApiKeys: List<ClientApiKey> = emptyList()

    fun updatePassword(password: String) {
        this.password = password
        this.updatedAt = LocalDateTime.now()
    }
    
    fun isSuperAdmin(): Boolean = role == ClientRole.SUPER_ADMIN
}

