package com.example.demo.adapter.out.client

import com.example.demo.model.Vendor
import org.springframework.stereotype.Component

@Component
class AiApiFactory(
    private val apiClients: List<AbstractAiCallTemplate>
) {
    fun getClient(vendor: Vendor): AbstractAiCallTemplate =
        apiClients.first { client -> client.getVendor() == vendor }
}