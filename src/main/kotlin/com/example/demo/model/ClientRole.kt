package com.example.demo.model

/**
 * Client 역할
 */
enum class ClientRole {
    /**
     * 일반 클라이언트 (자신의 정보만 조회 가능)
     */
    CLIENT,
    
    /**
     * 슈퍼 관리자 (모든 클라이언트 정보 조회 및 관리 가능)
     */
    SUPER_ADMIN
}
