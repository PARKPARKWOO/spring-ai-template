package com.example.demo.model.api

import com.example.demo.common.crypto.ApiKeyEncryptionConverter
import com.example.demo.model.ApiKeyTier
import com.example.demo.model.Vendor
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import java.time.LocalDateTime

@Entity
@DiscriminatorValue("OPENAI")
class OpenAiApiKey(
    id: Long = 0,
    applicationId: String,
    vendor: Vendor = Vendor.OPENAI,
    tier: ApiKeyTier = ApiKeyTier.FREE,
    description: String,
    createdAt: LocalDateTime,
    updatedAt: LocalDateTime,
    deletedAt: LocalDateTime? = null,

    @Column(name = "api_key", nullable = false, length = 512)
    @Convert(converter = ApiKeyEncryptionConverter::class)
    val apiKey: String,
) : ApiKey(id, applicationId, vendor, tier, description, createdAt, updatedAt, deletedAt) {
    
    override fun getApiKeyValue(): String = apiKey
}

