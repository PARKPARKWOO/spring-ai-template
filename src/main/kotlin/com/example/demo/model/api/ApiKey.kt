package com.example.demo.model.api

import com.example.demo.model.Vendor
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "vendor_type", discriminatorType = DiscriminatorType.STRING)
abstract class ApiKey(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val vendor: Vendor,

    val description: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime,

    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime,

    @Column(name = "deleted_at")
    val deletedAt: LocalDateTime? = null,
) {
    @OneToMany(mappedBy = "apiKey", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val clientApiKeys: List<com.example.demo.model.ClientApiKey> = emptyList()
    
    abstract fun getApiKeyValue(): String
}