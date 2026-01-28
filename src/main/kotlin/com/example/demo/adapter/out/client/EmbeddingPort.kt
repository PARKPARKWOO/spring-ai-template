package com.example.demo.adapter.out.client

import com.example.demo.dto.EmbeddingVendorOptions
import org.springframework.ai.embedding.EmbeddingResponse

/**
 * Embedding 모델 호출을 위한 인터페이스
 */
interface EmbeddingPort {
    /**
     * 단일 텍스트를 임베딩 벡터로 변환
     */
    suspend fun embed(
        text: String,
        clientId: Long,
        model: String? = null,
        vendorOptions: EmbeddingVendorOptions? = null,
    ): FloatArray

    /**
     * 여러 텍스트를 임베딩 벡터로 변환
     */
    suspend fun embed(
        texts: List<String>,
        clientId: Long,
        model: String? = null,
        vendorOptions: EmbeddingVendorOptions? = null,
    ): List<FloatArray>

    /**
     * 여러 텍스트를 임베딩 벡터로 변환 (메타데이터 포함)
     */
    suspend fun embedForResponse(
        texts: List<String>,
        clientId: Long,
        model: String? = null,
        vendorOptions: EmbeddingVendorOptions? = null,
    ): EmbeddingResponse
}
