package com.example.demo.model.api

import com.example.demo.model.Vendor
import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import java.time.LocalDateTime

@Entity
@DiscriminatorValue("GOOGLE")
class GoogleApiKey(
    id: Long = 0,
    vendor: Vendor = Vendor.GOOGLE,
    description: String,
    createdAt: LocalDateTime,
    updatedAt: LocalDateTime,
    deletedAt: LocalDateTime? = null,

    @Column(name = "api_key", nullable = false)
    val apiKey: String,

    @Column(name = "project_id")
    val projectId: String? = null,

    @Column(name = "location")
    val location: String? = null,
) : ApiKey(id, vendor, description, createdAt, updatedAt, deletedAt) {
    
    override fun getApiKeyValue(): String = apiKey
}

