package com.example.demo.dto

import com.example.demo.model.Vendor
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Embedding API 요청")
data class EmbeddingApiRequest(
    @field:Schema(description = "사용할 모델 정의", required = true)
    val models: List<EmbeddingModelSpec>,

    @field:Schema(description = "임베딩할 텍스트 목록", example = "[\"안녕하세요\", \"반갑습니다\"]", required = true)
    val texts: List<String>,
)

@Schema(description = "Embedding 모델 스펙")
@JsonDeserialize(using = EmbeddingModelSpecDeserializer::class)
data class EmbeddingModelSpec(
    @field:Schema(description = "사용할 AI 벤더", example = "OPENAI", required = true)
    val vendor: Vendor,

    @field:Schema(description = "모델 버전", example = "text-embedding-ada-002", required = true)
    val version: String,

    @field:Schema(description = "벤더별 옵션 (선택사항). 벤더에 따라 다른 옵션을 설정할 수 있습니다.")
    val vendorOptions: EmbeddingVendorOptions? = null,
)

@Schema(description = "Embedding 벤더별 옵션")
sealed class EmbeddingVendorOptions : VendorOptionsBase {
    @Schema(description = "OpenAI 전용 옵션")
    data class OpenAIOptions(
        @field:Schema(description = "임베딩 벡터의 차원 수 (text-embedding-3 이상에서만 지원)", example = "1536")
        val dimensions: Int? = null,

        @field:Schema(description = "인코딩 형식 (float 또는 base64)", example = "float")
        val encodingFormat: String? = null,

        @field:Schema(description = "사용자 식별자 (모니터링 및 남용 감지용)", example = "user-123")
        val user: String? = null,
    ) : EmbeddingVendorOptions()

    @Schema(description = "Google GenAI 전용 옵션")
    data class GeminiOptions(
        @field:Schema(description = "Task Type. 사용 목적에 따라 최적화된 임베딩을 생성합니다.", example = "RETRIEVAL_DOCUMENT")
        val taskType: String? = null,

        @field:Schema(description = "임베딩 벡터의 차원 수 (text-embedding-004 이상에서 지원)", example = "768")
        val dimensions: Int? = null,

        @field:Schema(description = "문서 제목 (RETRIEVAL_DOCUMENT taskType일 때만 유효)", example = "Product Documentation")
        val title: String? = null,

        @field:Schema(description = "자동 텍스트 잘림 여부. true일 경우 최대 길이 초과 시 자동으로 잘립니다.", example = "true")
        val autoTruncate: Boolean? = null,
    ) : EmbeddingVendorOptions()

    @Schema(description = "Grok (xAI) 전용 옵션")
    data class GrokOptions(
        @field:Schema(description = "임베딩 벡터의 차원 수", example = "1536")
        val dimensions: Int? = null,

        @field:Schema(description = "인코딩 형식 (float 또는 base64)", example = "float")
        val encodingFormat: String? = null,

        @field:Schema(description = "사용자 식별자", example = "user-123")
        val user: String? = null,
    ) : EmbeddingVendorOptions()
}
