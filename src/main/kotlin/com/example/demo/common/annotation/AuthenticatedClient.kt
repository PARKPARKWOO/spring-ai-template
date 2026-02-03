package com.example.demo.common.annotation

/**
 * 현재 인증된 클라이언트 정보를 주입받기 위한 어노테이션
 * 컨트롤러 메서드 파라미터에 사용
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class AuthenticatedClient
