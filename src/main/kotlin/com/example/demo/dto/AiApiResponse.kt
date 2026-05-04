package com.example.demo.dto

import com.example.demo.model.Vendor
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "AI API 응답")
data class AiApiResponse(
    @field:Schema(description = "AI 벤더", example = "OPENAI")
    val vendor: Vendor,

    @field:Schema(description = "AI 응답 결과", example = "안녕하세요! 무엇을 도와드릴까요?")
    val result: String,
    @field:Schema(description = "AI 응답 성공 여부", example = "false")
    val isError: Boolean = false,
    @field:Schema(
        description = "Fallback 으로 실제 사용된 vendor (예: ANTHROPIC). fallback=false 일 때는 vendor 와 동일.",
        example = "GOOGLE",
    )
    val usedVendor: Vendor? = null,
    @field:Schema(
        description = "Fallback 으로 실제 사용된 model 버전 (예: claude-sonnet-4-6). 비용/추적용.",
        example = "gemini-3-flash-preview",
    )
    val usedModel: String? = null,
) {
    companion object {
        fun failure(message: String, vendor: Vendor): AiApiResponse = AiApiResponse(
            vendor = vendor,
            result = message,
            isError = true,
        )
    }
}
