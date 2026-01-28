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
    private val clientId: Long,
    private val sessionId: String,
    private val model: String,
    private val vendor: String,
    private val contentType: String,
    private val promptToken: Int,
    private val completionToken: Int,
    @Column(columnDefinition = "TEXT")
    private val requestMessage: String,
    @Column(columnDefinition = "TEXT")
    private val responseMessage: String,
    private val createdAt: LocalDateTime,
) {
    companion object {
        fun create(
            clientId: Long,
            model: String,
            vendor: Vendor,
            contentType: ContentType,
            requestMessage: String,
            responseMessage: String,
            promptToken: Int,
            completionToken: Int,
            sessionId: String,
        ): AiUsageLogs = AiUsageLogs(
            id = 0L,
            clientId = clientId,
            model = model,
            vendor = vendor.name,
            contentType = contentType.name,
            requestMessage = requestMessage,
            responseMessage = responseMessage,
            createdAt = LocalDateTime.now(),
            promptToken = promptToken,
            completionToken = completionToken,
            sessionId = sessionId,
        )
    }
}