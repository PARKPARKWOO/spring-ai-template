package com.example.demo.business

import com.example.demo.adapter.client.AiApiFactory
import com.example.demo.model.Prompt
import com.example.demo.model.Vendor
import org.springframework.stereotype.Service

@Service
class AiService(
    private val aiApiFactory: AiApiFactory,
) {
    suspend fun call() {
        Vendor.values().forEach { vendor ->
            aiApiFactory.getClient(vendor).call()
        }
    }
}