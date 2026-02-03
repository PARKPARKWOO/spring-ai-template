package com.example.demo.model

import io.swagger.v3.oas.annotations.media.Schema

/**
 * API별 에러 코드 정의
 * 각 API에서 발생할 수 있는 구체적인 에러 케이스를 정의합니다.
 */
@Schema(description = "API 에러 코드")
enum class ApiErrorCode(
    @field:Schema(description = "에러 코드 값", example = "AUTH_INVALID_CREDENTIALS")
    val code: String,

    @field:Schema(description = "에러 메시지", example = "잘못된 인증 정보입니다.")
    val message: String,

    @field:Schema(description = "HTTP 상태 코드", example = "401")
    val httpStatus: Int,

    @field:Schema(description = "에러 카테고리", example = "AUTHENTICATION")
    val category: ErrorCategory
) {
    // ============================================
    // 인증 관련 에러 (Authentication)
    // ============================================
    @Schema(description = "인증 실패 - 잘못된 자격 증명")
    AUTH_INVALID_CREDENTIALS(
        "AUTH_INVALID_CREDENTIALS",
        "잘못된 사용자 이름 또는 비밀번호입니다.",
        401,
        ErrorCategory.AUTHENTICATION
    ),

    @Schema(description = "인증 실패 - 토큰 없음")
    AUTH_TOKEN_MISSING(
        "AUTH_TOKEN_MISSING",
        "인증 토큰이 제공되지 않았습니다. Authorization 헤더에 'Bearer {JWT토큰}' 또는 X-API-Key 헤더에 AppKey를 포함하세요.",
        401,
        ErrorCategory.AUTHENTICATION
    ),

    @Schema(description = "인증 실패 - 토큰 만료")
    AUTH_TOKEN_EXPIRED(
        "AUTH_TOKEN_EXPIRED",
        "인증 토큰이 만료되었습니다. 토큰을 갱신해주세요.",
        401,
        ErrorCategory.AUTHENTICATION
    ),

    @Schema(description = "인증 실패 - 토큰 무효")
    AUTH_TOKEN_INVALID(
        "AUTH_TOKEN_INVALID",
        "유효하지 않은 인증 토큰입니다.",
        401,
        ErrorCategory.AUTHENTICATION
    ),

    @Schema(description = "인증 실패 - AppKey 없음")
    AUTH_APP_KEY_MISSING(
        "AUTH_APP_KEY_MISSING",
        "AppKey가 제공되지 않았습니다. X-API-Key 헤더에 AppKey를 포함하세요.",
        401,
        ErrorCategory.AUTHENTICATION
    ),

    @Schema(description = "인증 실패 - AppKey 무효")
    AUTH_APP_KEY_INVALID(
        "AUTH_APP_KEY_INVALID",
        "유효하지 않은 AppKey입니다.",
        401,
        ErrorCategory.AUTHENTICATION
    ),

    @Schema(description = "인증 실패 - AppKey 비활성화")
    AUTH_APP_KEY_INACTIVE(
        "AUTH_APP_KEY_INACTIVE",
        "비활성화된 AppKey입니다.",
        401,
        ErrorCategory.AUTHENTICATION
    ),

    @Schema(description = "인증 실패 - AppKey 만료")
    AUTH_APP_KEY_EXPIRED(
        "AUTH_APP_KEY_EXPIRED",
        "만료된 AppKey입니다.",
        401,
        ErrorCategory.AUTHENTICATION
    ),

    @Schema(description = "인증 실패 - 인증 방식 충돌")
    AUTH_METHOD_CONFLICT(
        "AUTH_METHOD_CONFLICT",
        "인증 방식이 충돌합니다. Authorization 헤더(Bearer 토큰)와 X-API-Key 헤더(AppKey)를 동시에 제공할 수 없습니다. 하나만 제공해주세요.",
        400,
        ErrorCategory.AUTHENTICATION
    ),

    // ============================================
    // AI API 관련 에러 (AI Service)
    // ============================================
    @Schema(description = "AI API - API 키 없음")
    AI_API_KEY_NOT_FOUND(
        "AI_API_KEY_NOT_FOUND",
        "해당 벤더에 대한 활성화된 API 키를 찾을 수 없습니다.",
        404,
        ErrorCategory.AI_SERVICE
    ),

    @Schema(description = "AI API - 쿼터 초과")
    AI_QUOTA_EXCEEDED(
        "AI_QUOTA_EXCEEDED",
        "사용 가능한 쿼터를 초과했습니다.",
        429,
        ErrorCategory.AI_SERVICE
    ),

    @Schema(description = "AI API - 쿼터 할당 실패")
    AI_QUOTA_ALLOCATION_FAILED(
        "AI_QUOTA_ALLOCATION_FAILED",
        "쿼터 할당에 실패했습니다.",
        500,
        ErrorCategory.AI_SERVICE
    ),

    @Schema(description = "AI API - 벤더 미지원")
    AI_VENDOR_NOT_SUPPORTED(
        "AI_VENDOR_NOT_SUPPORTED",
        "지원하지 않는 벤더입니다.",
        400,
        ErrorCategory.AI_SERVICE
    ),

    @Schema(description = "AI API - 모델 오류")
    AI_MODEL_ERROR(
        "AI_MODEL_ERROR",
        "AI 모델 호출 중 오류가 발생했습니다.",
        500,
        ErrorCategory.AI_SERVICE
    ),

    @Schema(description = "AI API - 모델 타임아웃")
    AI_MODEL_TIMEOUT(
        "AI_MODEL_TIMEOUT",
        "AI 모델 호출이 시간 초과되었습니다.",
        504,
        ErrorCategory.AI_SERVICE
    ),

    @Schema(description = "AI API - 네트워크 오류")
    AI_NETWORK_ERROR(
        "AI_NETWORK_ERROR",
        "AI 서비스와의 통신 중 네트워크 오류가 발생했습니다.",
        503,
        ErrorCategory.AI_SERVICE
    ),

    @Schema(description = "AI API - 요청 검증 실패")
    AI_REQUEST_VALIDATION_FAILED(
        "AI_REQUEST_VALIDATION_FAILED",
        "요청 데이터가 유효하지 않습니다.",
        400,
        ErrorCategory.AI_SERVICE
    ),

    @Schema(description = "AI API - 모델 목록 비어있음")
    AI_MODELS_EMPTY(
        "AI_MODELS_EMPTY",
        "최소 하나의 모델이 필요합니다.",
        400,
        ErrorCategory.AI_SERVICE
    ),

    @Schema(description = "AI API - 시스템 프롬프트 필수 (스트리밍)")
    AI_SYSTEM_PROMPT_REQUIRED(
        "AI_SYSTEM_PROMPT_REQUIRED",
        "스트리밍 요청에는 systemPrompt가 필수입니다.",
        400,
        ErrorCategory.AI_SERVICE
    ),

    // ============================================
    // Embedding API 관련 에러 (Embedding Service)
    // ============================================
    @Schema(description = "Embedding API - API 키 없음")
    EMBEDDING_API_KEY_NOT_FOUND(
        "EMBEDDING_API_KEY_NOT_FOUND",
        "해당 벤더에 대한 활성화된 Embedding API 키를 찾을 수 없습니다.",
        404,
        ErrorCategory.EMBEDDING_SERVICE
    ),

    @Schema(description = "Embedding API - 벤더 미지원")
    EMBEDDING_VENDOR_NOT_SUPPORTED(
        "EMBEDDING_VENDOR_NOT_SUPPORTED",
        "해당 벤더는 Embedding을 지원하지 않습니다.",
        400,
        ErrorCategory.EMBEDDING_SERVICE
    ),

    @Schema(description = "Embedding API - 텍스트 목록 비어있음")
    EMBEDDING_TEXTS_EMPTY(
        "EMBEDDING_TEXTS_EMPTY",
        "임베딩할 텍스트 목록이 비어있습니다.",
        400,
        ErrorCategory.EMBEDDING_SERVICE
    ),

    @Schema(description = "Embedding API - 쿼터 초과")
    EMBEDDING_QUOTA_EXCEEDED(
        "EMBEDDING_QUOTA_EXCEEDED",
        "사용 가능한 Embedding 쿼터를 초과했습니다.",
        429,
        ErrorCategory.EMBEDDING_SERVICE
    ),

    @Schema(description = "Embedding API - 모델 오류")
    EMBEDDING_MODEL_ERROR(
        "EMBEDDING_MODEL_ERROR",
        "Embedding 모델 호출 중 오류가 발생했습니다.",
        500,
        ErrorCategory.EMBEDDING_SERVICE
    ),

    @Schema(description = "Embedding API - 모델 타임아웃")
    EMBEDDING_MODEL_TIMEOUT(
        "EMBEDDING_MODEL_TIMEOUT",
        "Embedding 모델 호출이 시간 초과되었습니다.",
        504,
        ErrorCategory.EMBEDDING_SERVICE
    ),

    @Schema(description = "Embedding API - 요청 검증 실패")
    EMBEDDING_REQUEST_VALIDATION_FAILED(
        "EMBEDDING_REQUEST_VALIDATION_FAILED",
        "요청 데이터가 유효하지 않습니다.",
        400,
        ErrorCategory.EMBEDDING_SERVICE
    ),

    @Schema(description = "Embedding API - 모델 목록 비어있음")
    EMBEDDING_MODELS_EMPTY(
        "EMBEDDING_MODELS_EMPTY",
        "최소 하나의 Embedding 모델이 필요합니다.",
        400,
        ErrorCategory.EMBEDDING_SERVICE
    ),

    // ============================================
    // Client API 관련 에러 (Client Service)
    // ============================================
    @Schema(description = "Client API - 클라이언트 없음")
    CLIENT_NOT_FOUND(
        "CLIENT_NOT_FOUND",
        "클라이언트를 찾을 수 없습니다.",
        404,
        ErrorCategory.CLIENT_SERVICE
    ),

    @Schema(description = "Client API - 클라이언트 삭제됨")
    CLIENT_DELETED(
        "CLIENT_DELETED",
        "삭제된 클라이언트입니다.",
        404,
        ErrorCategory.CLIENT_SERVICE
    ),

    @Schema(description = "Client API - 리프레시 토큰 무효")
    CLIENT_REFRESH_TOKEN_INVALID(
        "CLIENT_REFRESH_TOKEN_INVALID",
        "유효하지 않은 리프레시 토큰입니다.",
        401,
        ErrorCategory.CLIENT_SERVICE
    ),

    @Schema(description = "Client API - AppKey 없음")
    CLIENT_APP_KEY_NOT_FOUND(
        "CLIENT_APP_KEY_NOT_FOUND",
        "AppKey를 찾을 수 없습니다.",
        404,
        ErrorCategory.CLIENT_SERVICE
    ),

    @Schema(description = "Client API - AppKey 소유권 없음")
    CLIENT_APP_KEY_OWNERSHIP_DENIED(
        "CLIENT_APP_KEY_OWNERSHIP_DENIED",
        "해당 AppKey에 대한 권한이 없습니다.",
        403,
        ErrorCategory.CLIENT_SERVICE
    ),

    @Schema(description = "Client API - AppKey 이름 중복")
    CLIENT_APP_KEY_NAME_DUPLICATE(
        "CLIENT_APP_KEY_NAME_DUPLICATE",
        "이미 사용 중인 AppKey 이름입니다.",
        409,
        ErrorCategory.CLIENT_SERVICE
    ),

    // ============================================
    // 공통 에러 (Common)
    // ============================================
    @Schema(description = "공통 - 잘못된 요청")
    COMMON_BAD_REQUEST(
        "COMMON_BAD_REQUEST",
        "잘못된 요청입니다.",
        400,
        ErrorCategory.COMMON
    ),

    @Schema(description = "공통 - 권한 없음")
    COMMON_FORBIDDEN(
        "COMMON_FORBIDDEN",
        "이 작업을 수행할 권한이 없습니다.",
        403,
        ErrorCategory.COMMON
    ),

    @Schema(description = "공통 - 리소스 없음")
    COMMON_NOT_FOUND(
        "COMMON_NOT_FOUND",
        "요청한 리소스를 찾을 수 없습니다.",
        404,
        ErrorCategory.COMMON
    ),

    @Schema(description = "공통 - 서버 내부 오류")
    COMMON_INTERNAL_SERVER_ERROR(
        "COMMON_INTERNAL_SERVER_ERROR",
        "서버 내부 오류가 발생했습니다.",
        500,
        ErrorCategory.COMMON
    ),

    @Schema(description = "공통 - 서비스 사용 불가")
    COMMON_SERVICE_UNAVAILABLE(
        "COMMON_SERVICE_UNAVAILABLE",
        "서비스를 일시적으로 사용할 수 없습니다.",
        503,
        ErrorCategory.COMMON
    );
}

/**
 * 에러 카테고리
 */
@Schema(description = "에러 카테고리")
enum class ErrorCategory(
    @field:Schema(description = "카테고리 이름", example = "AUTHENTICATION")
    val categoryName: String,

    @field:Schema(description = "카테고리 설명", example = "인증 관련 에러")
    val description: String
) {
    @Schema(description = "인증 관련 에러")
    AUTHENTICATION("AUTHENTICATION", "인증 관련 에러"),

    @Schema(description = "AI 서비스 관련 에러")
    AI_SERVICE("AI_SERVICE", "AI 서비스 관련 에러"),

    @Schema(description = "Embedding 서비스 관련 에러")
    EMBEDDING_SERVICE("EMBEDDING_SERVICE", "Embedding 서비스 관련 에러"),

    @Schema(description = "클라이언트 서비스 관련 에러")
    CLIENT_SERVICE("CLIENT_SERVICE", "클라이언트 서비스 관련 에러"),

    @Schema(description = "공통 에러")
    COMMON("COMMON", "공통 에러");
}
