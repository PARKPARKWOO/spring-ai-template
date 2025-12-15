package com.example.demo.business

import com.example.demo.adapter.persistence.QuotaRepository
import com.example.demo.model.Vendor
import org.springframework.stereotype.Service

@Service
class QuotaService(
    private val quotaRepository: QuotaRepository,
) {
    fun isQuotaExceeded(clientId: Long, vendor: Vendor) {

    }
}