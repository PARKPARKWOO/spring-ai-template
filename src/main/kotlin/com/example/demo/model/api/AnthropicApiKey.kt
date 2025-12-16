package com.example.demo.model.api

import com.example.demo.model.Vendor
import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import java.time.LocalDateTime

@Entity
@DiscriminatorValue("ANTHROPIC")
class AnthropicApiKey(
    id: Long = 0,
    vendor: Vendor = Vendor.ANTHROPIC,
    description: String,
    createdAt: LocalDateTime,
    updatedAt: LocalDateTime,
    deletedAt: LocalDateTime? = null,

    @Column(name = "api_key", nullable = false)
    val apiKey: String,
) : ApiKey(id, vendor, description, createdAt, updatedAt, deletedAt) {
    
    override fun getApiKeyValue(): String = apiKey
}

