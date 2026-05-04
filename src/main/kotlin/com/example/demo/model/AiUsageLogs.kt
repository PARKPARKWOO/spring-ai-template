package com.example.demo.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import java.time.LocalDateTime

@Entity
class AiUsageLogs(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private val id: Long,

    @Column(name = "client_id")
    private val clientId: Long? = null,

    @Column(name = "api_key_id")
    private val apiKeyId: Long? = null,

    @Column(name = "application_id", length = 64)
    private val applicationId: String? = null,

    private val sessionId: String,
    private val model: String,
    private val vendor: String,
    private val contentType: String,
    private val promptToken: Int,
    private val completionToken: Int,
    private val createdAt: LocalDateTime,
) {
    companion object {
        fun create(
            apiKeyId: Long,
            applicationId: String?,
            model: String,
            vendor: Vendor,
            contentType: ContentType,
            promptToken: Int,
            completionToken: Int,
            sessionId: String,
            clientId: Long? = null,
        ): AiUsageLogs = AiUsageLogs(
            id = 0L,
            clientId = clientId,
            apiKeyId = apiKeyId,
            applicationId = applicationId,
            model = model,
            vendor = vendor.name,
            contentType = contentType.name,
            createdAt = LocalDateTime.now(),
            promptToken = promptToken,
            completionToken = completionToken,
            sessionId = sessionId,
        )
    }
}
