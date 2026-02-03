package com.example.demo.adapter.out.client

import com.example.demo.dto.EmbeddingVendorOptions
import org.springframework.ai.embedding.EmbeddingResponse

interface EmbeddingPort {
    suspend fun embed(
        text: String,
        applicationId: String,
        model: String? = null,
        vendorOptions: EmbeddingVendorOptions? = null,
    ): FloatArray

    suspend fun embed(
        texts: List<String>,
        applicationId: String,
        model: String? = null,
        vendorOptions: EmbeddingVendorOptions? = null,
    ): List<FloatArray>

    suspend fun embedForResponse(
        texts: List<String>,
        applicationId: String,
        model: String? = null,
        vendorOptions: EmbeddingVendorOptions? = null,
    ): EmbeddingResponse
}
