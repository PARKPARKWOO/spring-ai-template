package com.example.demo.common

import com.example.demo.adapter.out.persistence.ApiKeyRepository
import com.example.demo.adapter.out.persistence.ClientApiKeyRepository
import com.example.demo.adapter.out.persistence.ClientRepository
import com.example.demo.model.Client
import com.example.demo.model.ClientApiKey
import com.example.demo.model.Vendor
import com.example.demo.model.api.AnthropicApiKey
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.LocalDateTime

@Configuration
class ApiKeyInitializer(
    private val apiKeyRepository: ApiKeyRepository,
    private val clientRepository: ClientRepository,
    private val clientApiKeyRepository: ClientApiKeyRepository,
    private val passwordEncoder: PasswordEncoder,
) {
    @Value("\${spring.ai.anthropic.api-key}")
    private lateinit var anthropicApiKey: String

    //TODO: 해당 로직은 수정 필요함 지금은 로컬 테스트용이라 데이터가 없다는 가정하에
    @Bean
    fun initApiKeys(): ApplicationRunner {
        return ApplicationRunner {
            // 기본 Client 생성 또는 조회
            val defaultClient = clientRepository.findByName("HectoData") 
                ?: clientRepository.save(
                    Client(
                        id = 0L,
                        name = "HectoData",
                        password = passwordEncoder.encode("password"), // BCrypt로 암호화
                        description = "Default client for local testing",
                        createdAt = LocalDateTime.now(),
                        updatedAt = LocalDateTime.now(),
                        deletedAt = null
                    )
                )
            
            // Anthropic API Key 생성 또는 조회
            val anthropicKey = apiKeyRepository.findByVendor(Vendor.ANTHROPIC)
                ?: apiKeyRepository.save(
                    AnthropicApiKey(
                        id = 0L,
                        vendor = Vendor.ANTHROPIC,
                        description = "Anthropic API Key from application.yml",
                        createdAt = LocalDateTime.now(),
                        updatedAt = LocalDateTime.now(),
                        deletedAt = null,
                        apiKey = anthropicApiKey
                    )
                )
            
            // Client와 ApiKey 매핑 확인 및 생성
            val existingMapping = clientApiKeyRepository.findByClientIdAndApiKeyId(
                defaultClient.id,
                anthropicKey.id
            )
            
            if (existingMapping == null) {
                val now = LocalDateTime.now()
                clientApiKeyRepository.save(
                    ClientApiKey(
                        id = 0L,
                        client = defaultClient,
                        apiKey = anthropicKey,
                        isActive = true,
                        createdAt = now,
                        updatedAt = now,
                        deletedAt = null
                    )
                )
                println("Anthropic API Key mapped to client: ${defaultClient.name}")
            } else {
                println("Anthropic API Key already mapped to client: ${defaultClient.name}")
            }
        }
    }
}

