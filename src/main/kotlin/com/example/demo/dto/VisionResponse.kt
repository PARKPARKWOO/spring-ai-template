package com.example.demo.dto

import com.example.demo.model.Vendor
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Vision API 응답")
data class VisionResponse(
    @field:Schema(description = "AI 벤더", example = "GOOGLE")
    val vendor: Vendor,
    @field:Schema(description = "AI 응답 결과(텍스트)")
    val result: String,
    @field:Schema(description = "에러 여부", example = "false")
    val isError: Boolean = false,
) {
    companion object {
        fun failure(message: String, vendor: Vendor): VisionResponse =
            VisionResponse(vendor = vendor, result = message, isError = true)
    }
}
