package com.example.demo.common

import com.example.demo.business.exception.AiServiceException
import com.example.demo.business.exception.AuthenticationException
import com.example.demo.business.exception.ClientServiceException
import com.example.demo.business.exception.EmbeddingServiceException
import com.example.demo.dto.ApiError
import com.example.demo.dto.ApiResponse
import com.example.demo.dto.ResponseCode
import com.example.demo.model.ApiErrorCode
import com.fasterxml.jackson.databind.JsonMappingException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.resource.NoResourceFoundException
import org.springframework.web.server.ResponseStatusException

/**
 * 전역 예외 처리 핸들러
 * 모든 컨트롤러에서 발생하는 예외를 공통 응답 형식으로 변환
 */
@RestControllerAdvice
class GlobalExceptionHandler {
    
    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    
    /**
     * AI 서비스 예외 처리
     */
    @ExceptionHandler(AiServiceException::class)
    fun handleAiServiceException(ex: AiServiceException): ResponseEntity<ApiResponse<Nothing>> {
        logger.error("AiServiceException: ${ex.errorCode.code} - ${ex.message}", ex)
        
        val responseCode = when (ex.httpStatus) {
            HttpStatus.BAD_REQUEST -> ResponseCode.BAD_REQUEST
            HttpStatus.NOT_FOUND -> ResponseCode.NOT_FOUND
            HttpStatus.TOO_MANY_REQUESTS -> ResponseCode.SERVICE_UNAVAILABLE
            HttpStatus.GATEWAY_TIMEOUT -> ResponseCode.SERVICE_UNAVAILABLE
            HttpStatus.SERVICE_UNAVAILABLE -> ResponseCode.SERVICE_UNAVAILABLE
            HttpStatus.INTERNAL_SERVER_ERROR -> ResponseCode.INTERNAL_SERVER_ERROR
            else -> ResponseCode.ERROR
        }
        
        val error = ApiError(
            errorCode = ex.errorCode.code,
            message = ex.message ?: ex.errorCode.message,
            details = mapOf(
                "category" to ex.errorCode.category.categoryName,
                "httpStatus" to ex.httpStatus.value()
            )
        )
        
        val response = ApiResponse.failure<Nothing>(
            code = responseCode,
            message = ex.message ?: ex.errorCode.message,
            error = error
        )
        
        return ResponseEntity.status(ex.httpStatus).body(response)
    }
    
    /**
     * Embedding 서비스 예외 처리
     */
    @ExceptionHandler(EmbeddingServiceException::class)
    fun handleEmbeddingServiceException(ex: EmbeddingServiceException): ResponseEntity<ApiResponse<Nothing>> {
        logger.error("EmbeddingServiceException: ${ex.errorCode.code} - ${ex.message}", ex)
        
        val responseCode = when (ex.httpStatus) {
            HttpStatus.BAD_REQUEST -> ResponseCode.BAD_REQUEST
            HttpStatus.NOT_FOUND -> ResponseCode.NOT_FOUND
            HttpStatus.GATEWAY_TIMEOUT -> ResponseCode.SERVICE_UNAVAILABLE
            HttpStatus.SERVICE_UNAVAILABLE -> ResponseCode.SERVICE_UNAVAILABLE
            HttpStatus.INTERNAL_SERVER_ERROR -> ResponseCode.INTERNAL_SERVER_ERROR
            else -> ResponseCode.ERROR
        }
        
        val error = ApiError(
            errorCode = ex.errorCode.code,
            message = ex.message ?: ex.errorCode.message,
            details = mapOf(
                "category" to ex.errorCode.category.categoryName,
                "httpStatus" to ex.httpStatus.value()
            )
        )
        
        val response = ApiResponse.failure<Nothing>(
            code = responseCode,
            message = ex.message ?: ex.errorCode.message,
            error = error
        )
        
        return ResponseEntity.status(ex.httpStatus).body(response)
    }
    
    /**
     * Client 서비스 예외 처리
     */
    @ExceptionHandler(ClientServiceException::class)
    fun handleClientServiceException(ex: ClientServiceException): ResponseEntity<ApiResponse<Nothing>> {
        logger.error("ClientServiceException: ${ex.errorCode.code} - ${ex.message}", ex)
        
        val responseCode = when (ex.httpStatus) {
            HttpStatus.BAD_REQUEST -> ResponseCode.BAD_REQUEST
            HttpStatus.NOT_FOUND -> ResponseCode.NOT_FOUND
            HttpStatus.CONFLICT -> ResponseCode.BAD_REQUEST
            HttpStatus.FORBIDDEN -> ResponseCode.FORBIDDEN
            HttpStatus.INTERNAL_SERVER_ERROR -> ResponseCode.INTERNAL_SERVER_ERROR
            else -> ResponseCode.ERROR
        }
        
        val error = ApiError(
            errorCode = ex.errorCode.code,
            message = ex.message ?: ex.errorCode.message,
            details = mapOf(
                "category" to ex.errorCode.category.categoryName,
                "httpStatus" to ex.httpStatus.value()
            )
        )
        
        val response = ApiResponse.failure<Nothing>(
            code = responseCode,
            message = ex.message ?: ex.errorCode.message,
            error = error
        )
        
        return ResponseEntity.status(ex.httpStatus).body(response)
    }
    
    /**
     * 인증 예외 처리
     */
    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthenticationException(ex: AuthenticationException): ResponseEntity<ApiResponse<Nothing>> {
        logger.error("AuthenticationException: ${ex.errorCode.code} - ${ex.message}", ex)
        
        val error = ApiError(
            errorCode = ex.errorCode.code,
            message = ex.message ?: ex.errorCode.message,
            details = mapOf(
                "category" to ex.errorCode.category.categoryName,
                "httpStatus" to ex.httpStatus.value()
            )
        )
        
        val response = ApiResponse.failure<Nothing>(
            code = ResponseCode.UNAUTHORIZED,
            message = ex.message ?: ex.errorCode.message,
            error = error
        )
        
        return ResponseEntity.status(ex.httpStatus).body(response)
    }
    
    /**
     * ResponseStatusException 처리
     */
    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatusException(ex: ResponseStatusException): ResponseEntity<ApiResponse<Nothing>> {
        logger.error("ResponseStatusException: ${ex.message}", ex)
        
        val responseCode = when (ex.statusCode) {
            HttpStatus.BAD_REQUEST -> ResponseCode.BAD_REQUEST
            HttpStatus.UNAUTHORIZED -> ResponseCode.UNAUTHORIZED
            HttpStatus.FORBIDDEN -> ResponseCode.FORBIDDEN
            HttpStatus.NOT_FOUND -> ResponseCode.NOT_FOUND
            HttpStatus.INTERNAL_SERVER_ERROR -> ResponseCode.INTERNAL_SERVER_ERROR
            HttpStatus.SERVICE_UNAVAILABLE -> ResponseCode.SERVICE_UNAVAILABLE
            else -> ResponseCode.ERROR
        }
        
        // 인증 방식 충돌 에러인 경우 ApiErrorCode 포함
        val error = if (ex.reason?.contains("인증 방식이 충돌합니다") == true) {
            ApiError(
                errorCode = ApiErrorCode.AUTH_METHOD_CONFLICT.code,
                message = ex.reason ?: ApiErrorCode.AUTH_METHOD_CONFLICT.message,
                details = mapOf(
                    "category" to ApiErrorCode.AUTH_METHOD_CONFLICT.category.categoryName,
                    "httpStatus" to ex.statusCode.value()
                )
            )
        } else {
            null
        }
        
        val response = ApiResponse.failure<Nothing>(
            code = responseCode,
            message = ex.reason ?: "요청 처리 중 오류가 발생했습니다.",
            error = error
        )
        
        return ResponseEntity.status(ex.statusCode).body(response)
    }
    
    /**
     * JsonMappingException 처리 (Jackson 역직렬화 오류)
     * 커스텀 예외가 원인인 경우 해당 예외를 처리
     */
    @ExceptionHandler(JsonMappingException::class)
    fun handleJsonMappingException(ex: JsonMappingException): ResponseEntity<ApiResponse<Nothing>> {
        logger.error("JsonMappingException: ${ex.message}", ex)
        
        // 원인 예외가 커스텀 예외인 경우 해당 예외를 다시 던져서 적절한 핸들러가 처리하도록 함
        val cause = ex.cause
        when (cause) {
            is AiServiceException -> return handleAiServiceException(cause)
            is EmbeddingServiceException -> return handleEmbeddingServiceException(cause)
            is ClientServiceException -> return handleClientServiceException(cause)
            is AuthenticationException -> return handleAuthenticationException(cause)
        }
        
        // 커스텀 예외가 아닌 경우 일반적인 JSON 매핑 오류로 처리
        val error = ApiError(
            errorCode = ApiErrorCode.AI_REQUEST_VALIDATION_FAILED.code,
            message = ex.message ?: "요청 데이터를 파싱하는 중 오류가 발생했습니다."
        )
        
        val response = ApiResponse.failure<Nothing>(
            code = ResponseCode.BAD_REQUEST,
            message = ex.message ?: "요청 데이터를 파싱하는 중 오류가 발생했습니다.",
            error = error
        )
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response)
    }
    
    /**
     * IllegalArgumentException 처리 (fallback)
     * 커스텀 예외로 변환되지 않은 경우에만 사용
     */
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(ex: IllegalArgumentException): ResponseEntity<ApiResponse<Nothing>> {
        logger.error("IllegalArgumentException: ${ex.message}", ex)
        
        val error = ApiError(
            errorCode = ApiErrorCode.COMMON_BAD_REQUEST.code,
            message = ex.message ?: "잘못된 요청입니다."
        )
        
        val response = ApiResponse.failure<Nothing>(
            code = ResponseCode.BAD_REQUEST,
            message = ex.message ?: "잘못된 요청입니다.",
            error = error
        )
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response)
    }
    
    /**
     * UnsupportedOperationException 처리
     */
    @ExceptionHandler(UnsupportedOperationException::class)
    fun handleUnsupportedOperationException(ex: UnsupportedOperationException): ResponseEntity<ApiResponse<Nothing>> {
        logger.error("UnsupportedOperationException: ${ex.message}", ex)
        
        val error = ApiError(
            errorCode = ApiErrorCode.COMMON_BAD_REQUEST.code,
            message = ex.message ?: "지원하지 않는 작업입니다."
        )
        
        val response = ApiResponse.failure<Nothing>(
            code = ResponseCode.BAD_REQUEST,
            message = ex.message ?: "지원하지 않는 작업입니다.",
            error = error
        )
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response)
    }
    
    /**
     * 정적 리소스 없음 예외 처리 (favicon.ico 등)
     */
    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResourceFoundException(ex: NoResourceFoundException): ResponseEntity<ApiResponse<Nothing>> {
        // favicon.ico 같은 정적 리소스 요청은 로그를 남기지 않고 무시
        if (ex.resourcePath?.contains("favicon.ico") == true) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }
        
        logger.debug("NoResourceFoundException: ${ex.resourcePath}", ex)
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
    }
    
    /**
     * 일반 Exception 처리
     */
    @ExceptionHandler(Exception::class)
    fun handleException(ex: Exception): ResponseEntity<ApiResponse<Nothing>> {
        logger.error("Unexpected exception: ${ex.message}", ex)
        
        val error = ApiError(
            errorCode = ApiErrorCode.COMMON_INTERNAL_SERVER_ERROR.code,
            message = "서버 내부 오류가 발생했습니다."
        )
        
        val response = ApiResponse.failure<Nothing>(
            code = ResponseCode.INTERNAL_SERVER_ERROR,
            message = "서버 내부 오류가 발생했습니다.",
            error = error
        )
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response)
    }
}
