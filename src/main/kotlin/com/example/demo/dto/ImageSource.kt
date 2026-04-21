package com.example.demo.dto

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.swagger.v3.oas.annotations.media.Schema

/**
 * Vision(멀티모달) 입력 이미지 소스.
 *
 * JSON 직렬화 시 `source` 필드를 식별자로 사용.
 */
@Schema(description = "Vision 입력 이미지 소스 (base64 | url | storage_key 중 하나)")
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "source")
@JsonSubTypes(
    JsonSubTypes.Type(value = ImageSource.Base64::class, name = "base64"),
    JsonSubTypes.Type(value = ImageSource.Url::class, name = "url"),
    JsonSubTypes.Type(value = ImageSource.StorageKey::class, name = "storage_key"),
)
sealed class ImageSource {

    @Schema(description = "Base64 인코딩된 이미지 바이트")
    data class Base64(
        @field:Schema(description = "Base64 인코딩된 이미지 데이터 (data URL 접두사 제외)", required = true)
        val data: String,
        @field:Schema(description = "MIME 타입", example = "image/png", required = true)
        val mimeType: String,
    ) : ImageSource()

    @Schema(description = "URL로 접근 가능한 이미지. Storage presigned URL 권장.")
    data class Url(
        @field:Schema(description = "이미지 URL", example = "https://bucket.platformholder.site/foo/bar.jpg", required = true)
        val url: String,
    ) : ImageSource()

    @Schema(description = "Storage(MinIO) 버킷·키 참조. 내부 네트워크에서 직접 조회.")
    data class StorageKey(
        @field:Schema(description = "Storage 버킷", example = "mv", required = true)
        val bucket: String,
        @field:Schema(description = "Storage object key", example = "uploads/2026/abc.png", required = true)
        val key: String,
    ) : ImageSource()
}
