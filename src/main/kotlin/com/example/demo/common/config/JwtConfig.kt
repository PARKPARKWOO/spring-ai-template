package com.example.demo.common.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(JwtConfig::class)
@ConfigurationProperties(prefix = "jwt")
data class JwtConfig(
    var secret: String = "your-secret-key-change-this-in-production-use-a-long-random-string",
    var expiration: Long = 86400000, // 24시간 (밀리초)
    var refreshExpiration: Long = 604800000, // 7일 (밀리초)
    var header: String = "Authorization",
    var prefix: String = "Bearer "
) {
    init {
        // 기본값이 너무 짧으면 경고
        if (secret.length < 32) {
            println("WARNING: JWT secret is too short. Please set a longer secret key in application.yml")
        }
    }
}
