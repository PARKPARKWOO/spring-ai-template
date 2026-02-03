package com.example.demo.model

import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode

enum class ErrorCode(
    val httpStatusCode: Int,
    val message: String
) {
    EXCEEDED_QUOTA(HttpStatus.TOO_MANY_REQUESTS.value(), "사용량을 초과했습니다.")
}