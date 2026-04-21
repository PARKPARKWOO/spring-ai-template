package com.example.demo.adapter.`in`.api

import com.example.demo.business.VisionService
import com.example.demo.business.exception.AiServiceException
import com.example.demo.dto.ApiResponse
import com.example.demo.dto.ChatMessage
import com.example.demo.dto.ImageSource
import com.example.demo.dto.ModelSpec
import com.example.demo.dto.UserChatMessage
import com.example.demo.dto.VisionRequest
import com.example.demo.dto.VisionResponse
import com.example.demo.model.ApiErrorCode
import com.example.demo.model.Vendor
import com.fasterxml.jackson.databind.ObjectMapper
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.Base64

@RestController
@RequestMapping("/api/ai/vision")
@Tag(name = "AI Vision", description = "멀티모달 이미지 입력 AI API (Google Gemini 지원)")
class VisionController(
    private val visionService: VisionService,
    private val objectMapper: ObjectMapper,
) {

    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        summary = "Vision 호출 (JSON)",
        description =
            "텍스트 프롬프트와 이미지(base64 / url / storage_key)를 전달해 멀티모달 AI 응답을 받습니다. " +
                "이미지 호스트 화이트리스트: vision.allowed-hosts 설정. " +
                "현재 Google Gemini 전용. 예: gemini-3-flash-preview, gemini-3-pro.",
    )
    suspend fun vision(
        @RequestBody request: VisionRequest,
    ): ResponseEntity<ApiResponse<List<VisionResponse>>> {
        val applicationId = request.applicationId
            ?: throw AiServiceException(
                ApiErrorCode.AI_REQUEST_VALIDATION_FAILED,
                "applicationId is required",
            )
        val result = visionService.call(request, applicationId)
        return ResponseEntity.ok(
            ApiResponse.success(data = result, message = "Vision 응답을 성공적으로 받았습니다."),
        )
    }

    @PostMapping("/upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(
        summary = "Vision 호출 (multipart upload)",
        description =
            "파일 업로드 형태로 이미지를 전달하는 편의 엔드포인트. " +
                "JSON metadata 는 `metadata` part 에 VisionRequestMetadata 형식으로 전달.",
    )
    suspend fun visionUpload(
        @RequestPart("metadata") metadataJson: String,
        @RequestPart("images") files: List<MultipartFile>,
    ): ResponseEntity<ApiResponse<List<VisionResponse>>> {
        val meta = try {
            objectMapper.readValue(metadataJson, VisionRequestMetadata::class.java)
        } catch (e: Exception) {
            throw AiServiceException(
                ApiErrorCode.AI_REQUEST_VALIDATION_FAILED,
                "metadata 파싱 실패: ${e.message}",
            )
        }
        val applicationId = meta.applicationId
            ?: throw AiServiceException(
                ApiErrorCode.AI_REQUEST_VALIDATION_FAILED,
                "applicationId is required",
            )
        if (files.isEmpty()) {
            throw AiServiceException(
                ApiErrorCode.AI_REQUEST_VALIDATION_FAILED,
                "images 파일 파트가 비어있습니다.",
            )
        }

        val images: List<ImageSource> = files.map { f ->
            ImageSource.Base64(
                data = Base64.getEncoder().encodeToString(f.bytes),
                mimeType = f.contentType ?: "image/jpeg",
            )
        }

        val request = VisionRequest(
            applicationId = meta.applicationId,
            models = meta.models,
            messages = meta.messages.ifEmpty { listOf(UserChatMessage(content = "이 이미지들을 설명해주세요.")) },
            images = images,
            sessionId = meta.sessionId,
            timeoutSeconds = meta.timeoutSeconds,
            maxTokens = meta.maxTokens,
            requestType = meta.requestType,
        )

        val result = visionService.call(request, applicationId)
        return ResponseEntity.ok(
            ApiResponse.success(data = result, message = "Vision 응답을 성공적으로 받았습니다."),
        )
    }

    @Schema(description = "Multipart 업로드 시 metadata 부분의 스키마")
    data class VisionRequestMetadata(
        val applicationId: String? = null,
        val models: List<ModelSpec>,
        val messages: List<ChatMessage> = emptyList(),
        val sessionId: String,
        val timeoutSeconds: Int? = null,
        val maxTokens: Int? = null,
        val requestType: String? = null,
    )
}
