package com.example.demo.adapter.`in`.filter

import com.example.demo.adapter.out.persistence.ClientRepository
import com.example.demo.business.JwtService
import com.example.demo.common.config.JwtConfig
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * JWT 토큰을 검증하고 요청에 클라이언트 정보를 설정하는 필터
 */
@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
    private val jwtConfig: JwtConfig,
    private val clientRepository: ClientRepository
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        // AI API 엔드포인트는 AppKey만 사용하므로 JWT 필터 건너뛰기
        val requestPath = request.requestURI
        if (requestPath.startsWith("/api/ai")) {
            filterChain.doFilter(request, response)
            return
        }

        // 이미 AppKey로 인증된 경우 JWT 인증은 무시
        val existingAuthType = request.getAttribute("authType") as? String
        if (existingAuthType == "APP_KEY") {
            filterChain.doFilter(request, response)
            return
        }

        val authHeader = request.getHeader(jwtConfig.header)

        if (authHeader != null && authHeader.startsWith(jwtConfig.prefix)) {
            val token = jwtService.extractTokenFromHeader(authHeader)

            if (token != null && jwtService.validateToken(token) && jwtService.isAccessToken(token)) {
                val clientId = jwtService.getClientIdFromToken(token)
                val clientName = jwtService.getClientNameFromToken(token)

                if (clientId != null && clientName != null) {
                    // Client 엔티티에서 role 조회
                    val client = clientRepository.findById(clientId).orElse(null)
                    val clientRole = client?.role?.name ?: "CLIENT"
                    
                    // 요청 속성에 클라이언트 정보 설정
                    request.setAttribute("clientId", clientId)
                    request.setAttribute("clientName", clientName)
                    request.setAttribute("clientRole", clientRole)
                    request.setAttribute("authType", "JWT")
                }
            }
        }

        filterChain.doFilter(request, response)
    }
}