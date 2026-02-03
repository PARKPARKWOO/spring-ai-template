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
) {
    companion object {
        fun failure(message: String, vendor: Vendor): AiApiResponse = AiApiResponse(
            vendor = vendor,
            result = message,
            isError = true,
        )
    }
}
