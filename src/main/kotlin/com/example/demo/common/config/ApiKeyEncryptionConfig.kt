package com.example.demo.common.config

import com.example.demo.common.crypto.ApiKeyCryptoService
import com.example.demo.common.crypto.ApiKeyEncryptionHolder
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ApiKeyEncryptionConfig {

    @Bean
    fun apiKeyCryptoService(
        @Value("\${api-key.encryption.secret}")
        secretBase64: String,
    ): ApiKeyCryptoService = ApiKeyCryptoService(secretBase64)

    @PostConstruct
    fun init(apiKeyCryptoService: ApiKeyCryptoService) {
        ApiKeyEncryptionHolder.setCrypto(apiKeyCryptoService)
    }
}
