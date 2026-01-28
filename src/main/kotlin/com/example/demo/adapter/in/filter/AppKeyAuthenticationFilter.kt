package com.example.demo.adapter.`in`.filter

import com.example.demo.business.AppKeyService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * AppKey를 검증하고 요청에 클라이언트 정보를 설정하는 필터
 * X-API-Key 헤더에서 AppKey를 읽어서 검증합니다.
 */
@Component
class AppKeyAuthenticationFilter(
    @Lazy private val appKeyService: AppKeyService
) : OncePerRequestFilter() {

    companion object {
        private const val API_KEY_HEADER = "X-API-Key"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val requestPath = request.requestURI
        
        // AI API 엔드포인트는 Resolver에서 직접 처리하므로 필터에서 건너뛰기
        if (requestPath.startsWith("/api/ai")) {
            filterChain.doFilter(request, response)
            return
        }

        val apiKey = request.getHeader(API_KEY_HEADER)

        // 이미 JWT로 인증된 경우 AppKey는 무시 (JWT 우선)
        val existingAuthType = request.getAttribute("authType") as? String
        if (existingAuthType == "JWT") {
            filterChain.doFilter(request, response)
            return
        }

        // JWT가 없고 AppKey가 제공된 경우에만 AppKey 검증
        if (apiKey != null && apiKey.isNotBlank()) {
            val appKey = appKeyService.validateAppKey(apiKey)

            if (appKey != null) {
                // 요청 속성에 클라이언트 정보 설정
                request.setAttribute("clientId", appKey.client.id)
                request.setAttribute("clientName", appKey.client.name)
                request.setAttribute("clientRole", appKey.client.role.name)
                request.setAttribute("authType", "APP_KEY")
                request.setAttribute("appKeyId", appKey.id)
            }
        }

        filterChain.doFilter(request, response)
    }
}