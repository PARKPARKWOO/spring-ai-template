package com.example.demo.common.dto

/**
 * 인증된 클라이언트 정보
 * Stateless 방식: JWT 또는 AppKey만 사용
 */
data class ClientInfo(
    val clientId: Long,
    val clientName: String,
    val clientRole: String = "CLIENT", // "CLIENT", "SUPER_ADMIN"
    val authType: String, // "JWT", "APP_KEY"
    val appKeyId: Long? = null, // AppKey로 인증된 경우에만 값이 있음
)
