package com.example.demo.model.api

import com.example.demo.common.crypto.ApiKeyEncryptionConverter
import com.example.demo.model.Vendor
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import java.time.LocalDateTime

@Entity
@DiscriminatorValue("X_AI")
class XAiApiKey(
    id: Long = 0,
    applicationId: String,
    vendor: Vendor = Vendor.X_AI,
    description: String,
    createdAt: LocalDateTime,
    updatedAt: LocalDateTime,
    deletedAt: LocalDateTime? = null,

    @Column(name = "api_key", nullable = false, length = 512)
    @Convert(converter = ApiKeyEncryptionConverter::class)
    val apiKey: String,
) : ApiKey(id, applicationId, vendor, description, createdAt, updatedAt, deletedAt) {
    
    override fun getApiKeyValue(): String = apiKey
}

