package com.example.demo.common.config

import com.example.demo.common.crypto.ApiKeyCryptoService
import com.example.demo.common.crypto.ApiKeyEncryptionHolder
import jakarta.annotation.PostConstruct
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

@Configuration
class SecurityConfig(
    private val apiKeyCryptoService: ApiKeyCryptoService,
) {
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @PostConstruct
    fun initApiKeyEncryption() {
        ApiKeyEncryptionHolder.setCrypto(apiKeyCryptoService)
    }
}
