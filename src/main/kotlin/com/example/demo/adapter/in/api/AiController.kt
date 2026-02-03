package com.example.demo.adapter.`in`.api

import com.example.demo.business.AiService
import com.example.demo.business.EmbeddingService
import com.example.demo.dto.AiApiRequest
import com.example.demo.dto.AiApiResponse
import com.example.demo.dto.ApiResponse
import com.example.demo.dto.EmbeddingApiRequest
import com.example.demo.dto.EmbeddingApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@RestController
@RequestMapping("/api/ai")
@Tag(name = "AI", description = "AI 모델 호출 API")
class AiController(
    private val aiService: AiService,
    private val embeddingService: EmbeddingService,
) {
    @PostMapping
    @Operation(
        summary = "AI 모델 호출 (일반 응답)",
        description =
            "지정된 벤더(OpenAI, Anthropic, Google, xAI)의 AI 모델을 호출하여 JSON 응답을 받습니다. " +
                "스트리밍 응답이 필요한 경우 /api/ai/stream 엔드포인트를 사용하세요. " +
                "인증 방법: X-API-Key 헤더에 AppKey를 포함하세요. (AI API는 AppKey만 사용 가능합니다) " +
                "벤더별 옵션: Gemini(urlContexts, enableGoogleSearch, toolNames, useCachedContent, cachedContentName), " +
                "Anthropic(toolNames, cacheStrategy, cacheTtl), OpenAI(toolNames), Grok(toolNames). " +
                "타임아웃: timeoutSeconds 필드로 요청 타임아웃을 설정할 수 있습니다 (기본값: 120초).",
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(
                responseCode = "200",
                description = "성공적으로 AI 응답을 받았습니다",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema =
                            Schema(
                                implementation = ApiResponse::class,
                                example = "{\"code\":\"SUCCESS\",\"message\":\"AI 응답을 성공적으로 받았습니다.\",\"data\":[{\"vendor\":\"OPENAI\",\"result\":\"안녕하세요! 무엇을 도와드릴까요?\"}],\"error\":null}",
                            ),
                    ),
                ],
            ),
            SwaggerApiResponse(
                responseCode = "400",
                description = "잘못된 요청입니다. 가능한 errorCode: AI_VENDOR_NOT_SUPPORTED (지원하지 않는 벤더), AI_REQUEST_VALIDATION_FAILED (요청 검증 실패), AI_MODELS_EMPTY (모델 목록 비어있음)",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ApiResponse::class),
                    ),
                ],
            ),
            SwaggerApiResponse(
                responseCode = "401",
                description = "인증되지 않은 사용자입니다. 가능한 errorCode: AUTH_TOKEN_MISSING (인증 토큰 없음), AUTH_TOKEN_EXPIRED (토큰 만료), AUTH_TOKEN_INVALID (유효하지 않은 토큰), AUTH_APP_KEY_MISSING (AppKey 없음), AUTH_APP_KEY_INVALID (유효하지 않은 AppKey), AUTH_APP_KEY_INACTIVE (비활성화된 AppKey), AUTH_APP_KEY_EXPIRED (만료된 AppKey)",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ApiResponse::class),
                    ),
                ],
            ),
            SwaggerApiResponse(
                responseCode = "404",
                description = "해당 벤더에 대한 활성화된 API 키를 찾을 수 없습니다. errorCode: AI_API_KEY_NOT_FOUND",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ApiResponse::class),
                    ),
                ],
            ),
            SwaggerApiResponse(
                responseCode = "429",
                description = "사용 가능한 쿼터를 초과했습니다. errorCode: AI_QUOTA_EXCEEDED",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ApiResponse::class),
                    ),
                ],
            ),
            SwaggerApiResponse(
                responseCode = "500",
                description = "AI 모델 호출 중 오류가 발생했습니다. 가능한 errorCode: AI_MODEL_ERROR (AI 모델 오류), AI_QUOTA_ALLOCATION_FAILED (쿼터 할당 실패)",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ApiResponse::class),
                    ),
                ],
            ),
            SwaggerApiResponse(
                responseCode = "503",
                description = "AI 서비스와의 통신 중 네트워크 오류가 발생했습니다. errorCode: AI_NETWORK_ERROR",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ApiResponse::class),
                    ),
                ],
            ),
            SwaggerApiResponse(
                responseCode = "504",
                description = "AI 모델 호출이 시간 초과되었습니다. errorCode: AI_MODEL_TIMEOUT",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ApiResponse::class),
                    ),
                ],
            ),
        ],
    )
    suspend fun call(
        @RequestBody request: AiApiRequest,
    ): ResponseEntity<ApiResponse<List<AiApiResponse>>> {
        val applicationId =
            request.applicationId ?: throw com.example.demo.business.exception.AiServiceException(
                com.example.demo.model.ApiErrorCode.AI_REQUEST_VALIDATION_FAILED,
                "applicationId is required",
            )
        val result = aiService.call(request, applicationId)
        return ResponseEntity.ok(
            ApiResponse.success(
                data = result,
                message = "AI 응답을 성공적으로 받았습니다.",
            ),
        )
    }

    @PostMapping("/stream")
    @Operation(
        summary = "AI 모델 호출 (스트리밍 응답)",
        description =
            "지정된 벤더(OpenAI, Anthropic, Google, xAI)의 AI 모델을 호출하여 Server-Sent Events로 실시간 스트리밍 응답을 받습니다. " +
                "인증 방법: X-API-Key 헤더에 AppKey를 포함하세요. (AI API는 AppKey만 사용 가능합니다) " +
                "벤더별 옵션: Gemini(urlContexts, enableGoogleSearch, toolNames, useCachedContent, cachedContentName), " +
                "Anthropic(toolNames, cacheStrategy, cacheTtl), OpenAI(toolNames), Grok(toolNames). " +
                "타임아웃: timeoutSeconds 필드로 요청 타임아웃을 설정할 수 있습니다 (기본값: 120초).",
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(
                responseCode = "200",
                description = "실시간 스트리밍 응답",
                content = [
                    Content(
                        mediaType = "text/event-stream",
                        schema = Schema(implementation = String::class),
                    ),
                ],
            ),
            SwaggerApiResponse(
                responseCode = "400",
                description = "잘못된 요청입니다. 가능한 errorCode: AI_VENDOR_NOT_SUPPORTED, AI_REQUEST_VALIDATION_FAILED",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ApiResponse::class),
                    ),
                ],
            ),
            SwaggerApiResponse(
                responseCode = "401",
                description = "인증되지 않은 사용자입니다. 가능한 errorCode: AUTH_TOKEN_MISSING, AUTH_APP_KEY_INVALID",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ApiResponse::class),
                    ),
                ],
            ),
            SwaggerApiResponse(
                responseCode = "404",
                description = "해당 벤더에 대한 활성화된 API 키를 찾을 수 없습니다. errorCode: AI_API_KEY_NOT_FOUND",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ApiResponse::class),
                    ),
                ],
            ),
            SwaggerApiResponse(
                responseCode = "429",
                description = "사용 가능한 쿼터를 초과했습니다. errorCode: AI_QUOTA_EXCEEDED",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ApiResponse::class),
                    ),
                ],
            ),
            SwaggerApiResponse(
                responseCode = "500",
                description = "AI 모델 호출 중 오류가 발생했습니다. errorCode: AI_MODEL_ERROR",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ApiResponse::class),
                    ),
                ],
            ),
            SwaggerApiResponse(
                responseCode = "503",
                description = "AI 서비스와의 통신 중 네트워크 오류가 발생했습니다. errorCode: AI_NETWORK_ERROR",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ApiResponse::class),
                    ),
                ],
            ),
            SwaggerApiResponse(
                responseCode = "504",
                description = "AI 모델 호출이 시간 초과되었습니다. errorCode: AI_MODEL_TIMEOUT",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ApiResponse::class),
                    ),
                ],
            ),
        ],
    )
    suspend fun stream(
        @RequestBody request: AiApiRequest,
    ): ResponseEntity<Flux<String>> {
        val applicationId =
            request.applicationId ?: throw com.example.demo.business.exception.AiServiceException(
                com.example.demo.model.ApiErrorCode.AI_REQUEST_VALIDATION_FAILED,
                "applicationId is required",
            )
        val stream = aiService.stream(request, applicationId)
        return ResponseEntity
            .ok()
            .contentType(MediaType.TEXT_EVENT_STREAM)
            .body(stream)
    }

    @PostMapping("/embedding")
    @Operation(
        summary = "Embedding 모델 호출",
        description =
            "지정된 벤더(OpenAI, Google, xAI)의 Embedding 모델을 호출하여 텍스트를 벡터로 변환합니다. " +
                "인증 방법: X-API-Key 헤더에 AppKey를 포함하세요. (AI API는 AppKey만 사용 가능합니다)",
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(
                responseCode = "200",
                description = "성공적으로 Embedding 벡터를 생성했습니다",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema =
                            Schema(
                                implementation = ApiResponse::class,
                                example = "{\"code\":\"SUCCESS\",\"message\":\"Embedding 벡터를 성공적으로 생성했습니다.\",\"data\":[{\"vendor\":\"OPENAI\",\"embedding\":[0.1,0.2,0.3],\"dimension\":1536}],\"error\":null}",
                            ),
                    ),
                ],
            ),
            SwaggerApiResponse(
                responseCode = "400",
                description = "잘못된 요청입니다. 가능한 errorCode: EMBEDDING_VENDOR_NOT_SUPPORTED (지원하지 않는 벤더), EMBEDDING_TEXTS_EMPTY (텍스트 목록 비어있음)",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ApiResponse::class),
                    ),
                ],
            ),
            SwaggerApiResponse(
                responseCode = "401",
                description = "인증되지 않은 사용자입니다. 가능한 errorCode: AUTH_TOKEN_MISSING, AUTH_APP_KEY_INVALID",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ApiResponse::class),
                    ),
                ],
            ),
            SwaggerApiResponse(
                responseCode = "404",
                description = "해당 벤더에 대한 활성화된 API 키를 찾을 수 없습니다. errorCode: EMBEDDING_API_KEY_NOT_FOUND",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ApiResponse::class),
                    ),
                ],
            ),
            SwaggerApiResponse(
                responseCode = "429",
                description = "사용 가능한 쿼터를 초과했습니다. errorCode: EMBEDDING_QUOTA_EXCEEDED",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ApiResponse::class),
                    ),
                ],
            ),
            SwaggerApiResponse(
                responseCode = "500",
                description = "Embedding 모델 호출 중 오류가 발생했습니다.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ApiResponse::class),
                    ),
                ],
            ),
            SwaggerApiResponse(
                responseCode = "503",
                description = "Embedding 서비스와의 통신 중 네트워크 오류가 발생했습니다.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ApiResponse::class),
                    ),
                ],
            ),
        ],
    )
    suspend fun embedding(
        @RequestBody request: EmbeddingApiRequest,
    ): ResponseEntity<ApiResponse<List<EmbeddingApiResponse>>> {
        val applicationId =
            request.applicationId ?: throw com.example.demo.business.exception.AiServiceException(
                com.example.demo.model.ApiErrorCode.AI_REQUEST_VALIDATION_FAILED,
                "applicationId is required",
            )
        val result = embeddingService.embed(request, applicationId)
        return ResponseEntity.ok(
            ApiResponse.success(
                data = result,
                message = "Embedding 벡터를 성공적으로 생성했습니다.",
            ),
        )
    }
}
