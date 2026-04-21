package com.example.demo.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty

@Schema(description = "Vision(멀티모달 이미지 입력) API 요청")
data class VisionRequest(
    @field:Schema(description = "Application 식별자 (호출 서비스에서 전달, API 키 조회용)", example = "app-uuid-1")
    val applicationId: String? = null,
    @field:Schema(description = "사용할 모델 정의 (현재 Google/Gemini 만 지원)", required = true)
    @field:NotEmpty
    val models: List<ModelSpec>,
    @field:Schema(description = "텍스트 프롬프트 메시지 (이미지와 함께 전달)", required = true)
    @field:NotEmpty
    val messages: List<ChatMessage> = emptyList(),
    @field:Schema(description = "입력 이미지 목록", required = true)
    @field:NotEmpty
    val images: List<ImageSource> = emptyList(),
    @field:Schema(description = "세션 식별자", example = "vision-123", required = true)
    val sessionId: String,
    @field:Schema(description = "요청 타임아웃 (초). 기본 120초", example = "120")
    val timeoutSeconds: Int? = null,
    @field:Schema(description = "응답 최대 토큰 수. 기본 2000", example = "4000")
    val maxTokens: Int? = null,
    @field:Schema(description = "요청 타입 (로깅/모니터링용)", example = "image-ocr")
    val requestType: String? = null,
)
