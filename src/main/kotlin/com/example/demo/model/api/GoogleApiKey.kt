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
@DiscriminatorValue("GOOGLE")
class GoogleApiKey(
    id: Long = 0,
    applicationId: String? = null,
    vendor: Vendor = Vendor.GOOGLE,
    tier: ApiKeyTier = ApiKeyTier.FREE,
    description: String,
    createdAt: LocalDateTime,
    updatedAt: LocalDateTime,
    deletedAt: LocalDateTime? = null,

    @Column(name = "api_key", nullable = false, length = 512)
    @Convert(converter = ApiKeyEncryptionConverter::class)
    val apiKey: String,

    @Column(name = "project_id")
    val projectId: String? = null,

    @Column(name = "location")
    val location: String? = null,
) : ApiKey(id, applicationId, vendor, tier, description, createdAt, updatedAt, deletedAt) {
    
    override fun getApiKeyValue(): String = apiKey
}

