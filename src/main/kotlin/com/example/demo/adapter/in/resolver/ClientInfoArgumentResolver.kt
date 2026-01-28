package com.example.demo.adapter.`in`.resolver

import com.example.demo.business.AppKeyService
import com.example.demo.business.exception.AuthenticationException
import com.example.demo.common.annotation.AuthenticatedClient
import com.example.demo.common.annotation.CurrentClientId
import com.example.demo.common.dto.ClientInfo
import com.example.demo.model.ApiErrorCode
import jakarta.servlet.http.HttpServletRequest
import org.springframework.context.annotation.Lazy
import org.springframework.core.MethodParameter
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

/**
 * 클라이언트 인증 정보를 컨트롤러 메서드 파라미터로 주입하는 Resolver
 * Stateless 방식: JWT 또는 AppKey만 사용 (세션 미사용)
 * 
 * AI API 요청의 경우: Resolver에서 직접 AppKey를 검증 (캐시 활용)
 * 기타 API 요청의 경우: 필터에서 설정한 request attribute 사용
 */
@Component
class ClientInfoArgumentResolver(
    @Lazy private val appKeyService: AppKeyService
) : HandlerMethodArgumentResolver {
    
    companion object {
        private const val API_KEY_HEADER = "X-API-Key"
    }

    override fun supportsParameter(parameter: MethodParameter): Boolean {
        return parameter.hasParameterAnnotation(CurrentClientId::class.java) ||
                parameter.hasParameterAnnotation(AuthenticatedClient::class.java)
    }

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?
    ): Any? {
        val request = webRequest.getNativeRequest(HttpServletRequest::class.java)
            ?: throw IllegalStateException("HttpServletRequest not found")

        val requestPath = request.requestURI
        val isAiRequest = requestPath.startsWith("/api/ai")

        // AI 요청인 경우: Resolver에서 직접 AppKey 검증 (캐시 활용)
        if (isAiRequest) {
            return resolveAiRequest(parameter, request)
        }

        // 기타 요청인 경우: 필터에서 설정한 request attribute 사용
        return resolveOtherRequest(parameter, request)
    }

    /**
     * AI 요청 처리: Resolver에서 직접 AppKey 검증
     */
    private fun resolveAiRequest(parameter: MethodParameter, request: HttpServletRequest): Any? {
        val apiKey = request.getHeader(API_KEY_HEADER)
            ?: throw AuthenticationException(
                ApiErrorCode.AUTH_APP_KEY_MISSING,
                "AppKey가 제공되지 않았습니다. X-API-Key 헤더에 AppKey를 포함하세요."
            )

        // AppKey 검증 (캐시 활용)
        val appKey = appKeyService.validateAppKey(apiKey)
            ?: throw AuthenticationException(
                ApiErrorCode.AUTH_APP_KEY_INVALID,
                "유효하지 않은 AppKey입니다."
            )

        // @CurrentClientId 어노테이션이 있는 경우 Long (clientId) 반환
        if (parameter.hasParameterAnnotation(CurrentClientId::class.java)) {
            return appKey.client.id
        }

        // @AuthenticatedClient 어노테이션이 있는 경우 ClientInfo 반환
        if (parameter.hasParameterAnnotation(AuthenticatedClient::class.java)) {
            return ClientInfo(
                clientId = appKey.client.id,
                clientName = appKey.client.name,
                clientRole = appKey.client.role.name,
                authType = "APP_KEY",
                appKeyId = appKey.id
            )
        }

        return null
    }

    /**
     * 기타 요청 처리: 필터에서 설정한 request attribute 사용
     */
    private fun resolveOtherRequest(parameter: MethodParameter, request: HttpServletRequest): Any? {
        // @CurrentClientId 어노테이션이 있는 경우 Long (clientId) 반환
        if (parameter.hasParameterAnnotation(CurrentClientId::class.java)) {
            val clientId = getClientId(request)
            if (clientId == null) {
                throw AuthenticationException(
                    ApiErrorCode.AUTH_TOKEN_MISSING,
                    "Please provide valid JWT token or AppKey (X-API-Key header)"
                )
            }
            return clientId
        }

        // @AuthenticatedClient 어노테이션이 있는 경우 ClientInfo 반환
        if (parameter.hasParameterAnnotation(AuthenticatedClient::class.java)) {
            val clientId = getClientId(request)
            if (clientId == null) {
                throw AuthenticationException(
                    ApiErrorCode.AUTH_TOKEN_MISSING,
                    "Please provide valid JWT token or AppKey (X-API-Key header)"
                )
            }
            
            val clientName = getClientName(request) ?: ""
            val clientRole = getClientRole(request) ?: "CLIENT"
            val authType = getAuthType(request) ?: "UNKNOWN"
            val appKeyId = request.getAttribute("appKeyId") as? Long

            return ClientInfo(
                clientId = clientId,
                clientName = clientName,
                clientRole = clientRole,
                authType = authType,
                appKeyId = appKeyId
            )
        }

        return null
    }

    /**
     * 요청에서 클라이언트 ID 추출 (JWT 또는 AppKey만 사용, Stateless)
     */
    private fun getClientId(request: HttpServletRequest): Long? {
        // JWT 또는 AppKey로 인증된 경우 (필터에서 설정됨)
        return request.getAttribute("clientId") as? Long
    }

    /**
     * 요청에서 클라이언트 이름 추출 (Stateless)
     */
    private fun getClientName(request: HttpServletRequest): String? {
        return request.getAttribute("clientName") as? String
    }

    /**
     * 요청에서 인증 타입 추출 (Stateless)
     */
    private fun getAuthType(request: HttpServletRequest): String? {
        return request.getAttribute("authType") as? String
    }
    
    /**
     * 요청에서 클라이언트 역할 추출 (Stateless)
     */
    private fun getClientRole(request: HttpServletRequest): String? {
        return request.getAttribute("clientRole") as? String
    }
}
