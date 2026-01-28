package com.example.demo.business.exception

import com.example.demo.model.ErrorCode

class BusinessException(
    private val errorCode: ErrorCode,
): RuntimeException(errorCode.message) {
}