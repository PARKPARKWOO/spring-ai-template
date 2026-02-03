package com.example.demo.business.exception

import com.example.demo.model.ApiErrorCode
import org.springframework.http.HttpStatus

/**
 * Client 서비스 관련 예외
 */
class ClientServiceException(
    val errorCode: ApiErrorCode,
    message: String? = null,
    cause: Throwable? = null
) : RuntimeException(message ?: errorCode.message, cause) {
    val httpStatus: HttpStatus = HttpStatus.valueOf(errorCode.httpStatus)
}
