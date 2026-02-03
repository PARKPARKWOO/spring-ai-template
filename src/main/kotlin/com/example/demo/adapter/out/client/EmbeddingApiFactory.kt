package com.example.demo.adapter.out.client

import com.example.demo.model.Vendor
import org.springframework.stereotype.Component

@Component
class EmbeddingApiFactory(
    private val embeddingClients: List<AbstractEmbeddingTemplate>
) {
    fun getClient(vendor: Vendor): AbstractEmbeddingTemplate =
        embeddingClients.first { client -> client.getVendor() == vendor }
}
