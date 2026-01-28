package com.example.demo.business

import com.example.demo.common.config.JwtConfig
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Service
import java.util.*

@Service
class JwtService(
    private val jwtConfig: JwtConfig
) {
    private val secretKey = Keys.hmacShaKeyFor(jwtConfig.secret.toByteArray())

    /**
     * JWT 토큰 생성
     * @param clientId 클라이언트 ID
     * @param clientName 클라이언트 이름
     * @param isRefreshToken 리프레시 토큰 여부
     * @return JWT 토큰 문자열
     */
    fun generateToken(
        clientId: Long,
        clientName: String,
        isRefreshToken: Boolean = false
    ): String {
        val expiration = if (isRefreshToken) {
            jwtConfig.refreshExpiration
        } else {
            jwtConfig.expiration
        }

        val now = Date()
        val expiryDate = Date(now.time + expiration)

        return Jwts.builder()
            .subject(clientId.toString())
            .claim("clientName", clientName)
            .claim("type", if (isRefreshToken) "refresh" else "access")
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(secretKey)
            .compact()
    }

    /**
     * JWT 토큰에서 클라이언트 ID 추출
     */
    fun getClientIdFromToken(token: String): Long? {
        return try {
            val claims = getClaimsFromToken(token)
            claims.subject.toLongOrNull()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * JWT 토큰에서 클라이언트 이름 추출
     */
    fun getClientNameFromToken(token: String): String? {
        return try {
            val claims = getClaimsFromToken(token)
            claims["clientName"] as? String
        } catch (e: Exception) {
            null
        }
    }

    /**
     * JWT 토큰에서 Claims 추출
     */
    fun getClaimsFromToken(token: String): Claims {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .payload
    }

    /**
     * JWT 토큰 유효성 검증
     */
    fun validateToken(token: String): Boolean {
        return try {
            val claims = getClaimsFromToken(token)
            !claims.expiration.before(Date())
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 토큰이 리프레시 토큰인지 확인
     */
    fun isRefreshToken(token: String): Boolean {
        return try {
            val claims = getClaimsFromToken(token)
            claims["type"] == "refresh"
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 토큰이 액세스 토큰인지 확인
     */
    fun isAccessToken(token: String): Boolean {
        return try {
            val claims = getClaimsFromToken(token)
            claims["type"] != "refresh"
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 토큰에서 Bearer 접두사 제거
     */
    fun extractTokenFromHeader(authHeader: String?): String? {
        if (authHeader == null || !authHeader.startsWith(jwtConfig.prefix)) {
            return null
        }
        return authHeader.substring(jwtConfig.prefix.length)
    }
}
