package com.example.demo.dto

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 공통 API 응답 래퍼
 * 
 * @param T 응답 데이터 타입
 */
@Schema(description = "공통 API 응답")
data class ApiResponse<T>(
    @field:Schema(description = "응답 코드", example = "SUCCESS", required = true)
    val code: ResponseCode,

    @field:Schema(description = "응답 메시지", example = "요청이 성공적으로 처리되었습니다.")
    val message: String,

    @field:Schema(description = "응답 데이터")
    val data: T? = null,

    @field:Schema(description = "에러 상세 정보 (실패 시)")
    val error: ApiError? = null,
) {
    companion object {
        /**
         * 성공 응답 생성
         */
        fun <T> success(data: T, message: String = "요청이 성공적으로 처리되었습니다."): ApiResponse<T> {
            return ApiResponse(
                code = ResponseCode.SUCCESS,
                message = message,
                data = data
            )
        }
        
        /**
         * 성공 응답 생성 (데이터 없음)
         */
        fun success(message: String = "요청이 성공적으로 처리되었습니다."): ApiResponse<Unit> {
            return ApiResponse(
                code = ResponseCode.SUCCESS,
                message = message,
                data = null
            )
        }
        
        /**
         * 실패 응답 생성
         */
        fun <T> failure(
            code: ResponseCode,
            message: String,
            error: ApiError? = null
        ): ApiResponse<T> {
            return ApiResponse(
                code = code,
                message = message,
                data = null,
                error = error
            )
        }
        
        /**
         * 실패 응답 생성 (간단한 버전)
         */
        fun <T> failure(message: String, errorCode: String? = null): ApiResponse<T> {
            return ApiResponse(
                code = ResponseCode.ERROR,
                message = message,
                data = null,
                error = errorCode?.let { ApiError(errorCode = it, message = message) }
            )
        }
    }
}

/**
 * 응답 코드 Enum
 */
@Schema(description = "API 응답 코드")
enum class ResponseCode(
    @field:Schema(description = "응답 코드 값", example = "SUCCESS")
    val value: String,

    @field:Schema(description = "HTTP 상태 코드", example = "200")
    val httpStatus: Int
) {
    @Schema(description = "성공")
    SUCCESS("SUCCESS", 200),
    
    @Schema(description = "일반 오류")
    ERROR("ERROR", 500),
    
    @Schema(description = "잘못된 요청")
    BAD_REQUEST("BAD_REQUEST", 400),
    
    @Schema(description = "인증 실패")
    UNAUTHORIZED("UNAUTHORIZED", 401),
    
    @Schema(description = "권한 없음")
    FORBIDDEN("FORBIDDEN", 403),
    
    @Schema(description = "리소스를 찾을 수 없음")
    NOT_FOUND("NOT_FOUND", 404),
    
    @Schema(description = "서버 내부 오류")
    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", 500),
    
    @Schema(description = "서비스 사용 불가")
    SERVICE_UNAVAILABLE("SERVICE_UNAVAILABLE", 503),
}

/**
 * API 에러 정보
 */
@Schema(
    description = "API 에러 정보. " +
            "errorCode는 ApiErrorCode enum 값입니다. " +
            "주요 에러 코드: " +
            "AUTH_INVALID_CREDENTIALS, AUTH_TOKEN_MISSING, AUTH_TOKEN_EXPIRED, " +
            "AI_API_KEY_NOT_FOUND, AI_QUOTA_EXCEEDED, AI_VENDOR_NOT_SUPPORTED, " +
            "EMBEDDING_API_KEY_NOT_FOUND, EMBEDDING_QUOTA_EXCEEDED, " +
            "CLIENT_NOT_FOUND, CLIENT_APP_KEY_NOT_FOUND 등. " +
            "전체 에러 코드 목록은 ApiErrorCode enum을 참조하세요."
)
data class ApiError(
    @field:Schema(
        description = "에러 코드 (ApiErrorCode enum 값). " +
                "예: AUTH_INVALID_CREDENTIALS, AI_QUOTA_EXCEEDED, EMBEDDING_API_KEY_NOT_FOUND 등",
        example = "AUTH_INVALID_CREDENTIALS",
        allowableValues = [
            "AUTH_INVALID_CREDENTIALS",
            "AUTH_TOKEN_MISSING",
            "AUTH_TOKEN_EXPIRED",
            "AUTH_TOKEN_INVALID",
            "AUTH_APP_KEY_MISSING",
            "AUTH_APP_KEY_INVALID",
            "AUTH_APP_KEY_INACTIVE",
            "AUTH_APP_KEY_EXPIRED",
            "AI_API_KEY_NOT_FOUND",
            "AI_QUOTA_EXCEEDED",
            "AI_QUOTA_ALLOCATION_FAILED",
            "AI_VENDOR_NOT_SUPPORTED",
            "AI_MODEL_ERROR",
            "AI_MODEL_TIMEOUT",
            "AI_NETWORK_ERROR",
            "AI_REQUEST_VALIDATION_FAILED",
            "EMBEDDING_API_KEY_NOT_FOUND",
            "EMBEDDING_VENDOR_NOT_SUPPORTED",
            "EMBEDDING_TEXTS_EMPTY",
            "EMBEDDING_QUOTA_EXCEEDED",
            "CLIENT_NOT_FOUND",
            "CLIENT_DELETED",
            "CLIENT_REFRESH_TOKEN_INVALID",
            "CLIENT_APP_KEY_NOT_FOUND",
            "CLIENT_APP_KEY_OWNERSHIP_DENIED",
            "CLIENT_APP_KEY_NAME_DUPLICATE",
            "COMMON_BAD_REQUEST",
            "COMMON_FORBIDDEN",
            "COMMON_NOT_FOUND",
            "COMMON_INTERNAL_SERVER_ERROR",
            "COMMON_SERVICE_UNAVAILABLE"
        ]
    )
    val errorCode: String,

    @field:Schema(description = "에러 메시지", example = "잘못된 사용자 이름 또는 비밀번호입니다.")
    val message: String,

    @field:Schema(
        description = "에러 상세 정보 (Map 형태). " +
                "일반적으로 category (에러 카테고리), httpStatus (HTTP 상태 코드) 등을 포함합니다.",
        example = "{\"category\":\"AUTHENTICATION\",\"httpStatus\":401}"
    )
    val details: Map<String, Any>? = null,

    @field:Schema(description = "스택 트레이스 (개발 환경에서만 제공)")
    val stackTrace: String? = null,
)
