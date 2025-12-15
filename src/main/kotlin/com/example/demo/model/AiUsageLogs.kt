package com.example.demo.model

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
    private val model: String,
    private val vendor: String,
    private val promptToken: Int,
    private val completionToken: Int,
    private val requestMessage: String,
    private val responseMessage: String,
    private val createdAt: LocalDateTime,
) {
    companion object {
        fun create(
            clientId: Long,
            model: String,
            vendor: Vendor,
            requestMessage: String,
            responseMessage: String,
            promptToken: Int,
            completionToken: Int,
        ): AiUsageLogs = AiUsageLogs(
            id = 0L,
            clientId = clientId,
            model = model,
            vendor = vendor.name,
            requestMessage = requestMessage,
            responseMessage = responseMessage,
            createdAt = LocalDateTime.now(),
            promptToken = promptToken,
            completionToken = completionToken,
        )
    }
}