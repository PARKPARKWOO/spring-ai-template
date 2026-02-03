package com.example.demo.dto

import com.example.demo.model.Vendor
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Embedding API 응답")
data class EmbeddingApiResponse(
    @field:Schema(description = "사용된 벤더", example = "OPENAI")
    val vendor: Vendor,

    @field:Schema(description = "임베딩 벡터 목록 (각 텍스트에 대한 벡터 배열)", example = "[[0.1, 0.2, ...], [0.3, 0.4, ...]]")
    val embeddings: List<FloatArray>,

    @field:Schema(description = "벡터 차원 수", example = "1536")
    val dimensions: Int? = null,

    @field:Schema(description = "사용된 모델", example = "text-embedding-ada-002")
    val model: String? = null,
)
