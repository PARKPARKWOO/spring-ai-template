package com.example.demo.common

import com.example.demo.adapter.out.persistence.ApiKeyRepository
import com.example.demo.adapter.out.persistence.ClientApiKeyRepository
import com.example.demo.adapter.out.persistence.ClientRepository
import com.example.demo.adapter.out.persistence.QuotaRepository
import com.example.demo.common.constants.CycleUnit
import com.example.demo.model.Client
import com.example.demo.model.ClientApiKey
import com.example.demo.model.Quota
import com.example.demo.model.Vendor
import com.example.demo.model.api.AnthropicApiKey
import com.example.demo.model.api.GoogleApiKey
import com.example.demo.model.api.OpenAiApiKey
import com.example.demo.model.api.XAiApiKey
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Configuration
class ApiKeyInitializer(
    private val apiKeyRepository: ApiKeyRepository,
    private val clientRepository: ClientRepository,
    private val clientApiKeyRepository: ClientApiKeyRepository,
    private val passwordEncoder: PasswordEncoder,
    private val quotaRepository: QuotaRepository,
) {
    @Value("\${spring.ai.anthropic.api-key}")
    private lateinit var anthropicApiKey: String

    @Value("\${spring.ai.openai.api-key}")
    private lateinit var openAiApiKey: String

    @Value("\${spring.ai.google.genai.api-key}")
    private lateinit var geminiApiKey: String

    @Value("\${spring.ai.grok.api-key}")
    private lateinit var grokApiKey: String

    //TODO: 해당 로직은 수정 필요함 지금은 로컬 테스트용이라 데이터가 없다는 가정하에
    @Bean
    fun initApiKeys(): ApplicationRunner {
        return ApplicationRunner {
            updateClientPasswords()
        }
    }
    
    @Transactional
    fun updateClientPasswords() {
        // HectoData 클라이언트의 비밀번호를 "password"로 업데이트
        val hectoDataClient = clientRepository.findByName("HectoData")
        if (hectoDataClient != null && hectoDataClient.deletedAt == null) {
            println("HectoData 클라이언트 발견 - 현재 비밀번호 해시: ${hectoDataClient.password.take(30)}...")
            // BCrypt는 매번 다른 해시를 생성하므로 matches로 확인
            val currentPasswordMatches = passwordEncoder.matches("password", hectoDataClient.password)
            println("HectoData 현재 비밀번호 매칭 결과: $currentPasswordMatches")
            
            if (!currentPasswordMatches) {
                // 비밀번호가 일치하지 않으면 업데이트
                val newPasswordHash = passwordEncoder.encode("password")
                println("HectoData 새 비밀번호 해시 생성: ${newPasswordHash.take(30)}...")
                hectoDataClient.updatePassword(newPasswordHash)
                val savedClient = clientRepository.save(hectoDataClient)
                println("HectoData 비밀번호 저장 완료 - 저장된 클라이언트 ID: ${savedClient.id}")
                
                // 저장 후 즉시 다시 조회하여 검증
                clientRepository.flush() // DB에 즉시 반영
                val verifiedClient = clientRepository.findByName("HectoData")
                if (verifiedClient != null) {
                    val verificationResult = passwordEncoder.matches("password", verifiedClient.password)
                    if (verificationResult) {
                        println("✓ HectoData 클라이언트의 비밀번호가 'password'로 업데이트되었습니다. (검증 완료)")
                    } else {
                        println("✗ 경고: HectoData 클라이언트의 비밀번호 업데이트 후 검증에 실패했습니다.")
                        println("  저장된 해시: ${verifiedClient.password.take(30)}...")
                    }
                }
            } else {
                // 이미 올바르게 설정되어 있다고 나오지만, 실제로 로그인 시 검증을 위해 추가 확인
                println("✓ HectoData 클라이언트의 비밀번호가 이미 올바르게 설정되어 있습니다.")
                // 추가 검증: 실제로 로그인 가능한지 다시 한 번 확인
                val doubleCheck = passwordEncoder.matches("password", hectoDataClient.password)
                println("  HectoData 이중 검증 결과: $doubleCheck")
                if (!doubleCheck) {
                    println("  ⚠️ 경고: 이중 검증 실패! 비밀번호를 강제로 업데이트합니다.")
                    hectoDataClient.updatePassword(passwordEncoder.encode("password"))
                    clientRepository.save(hectoDataClient)
                    clientRepository.flush()
                }
            }
        } else {
            println("경고: HectoData 클라이언트를 찾을 수 없습니다.")
        }
        
        // HectoFinancial 클라이언트의 비밀번호를 "password"로 업데이트
        // DB에 저장된 BCrypt 해시값이 실제 비밀번호와 일치하지 않을 수 있으므로
        // 애플리케이션 시작 시 항상 올바른 해시값으로 업데이트
        val hectoFinancialClient = clientRepository.findByName("HectoFinancial")
        if (hectoFinancialClient != null && hectoFinancialClient.deletedAt == null) {
            println("HectoFinancial 클라이언트 발견 - 현재 비밀번호 해시: ${hectoFinancialClient.password.take(30)}...")
            // BCrypt는 매번 다른 해시를 생성하므로 matches로 확인
            val currentPasswordMatches = passwordEncoder.matches("password", hectoFinancialClient.password)
            println("현재 비밀번호 매칭 결과: $currentPasswordMatches")
            
            if (!currentPasswordMatches) {
                // 비밀번호가 일치하지 않으면 업데이트
                val newPasswordHash = passwordEncoder.encode("password")
                println("새 비밀번호 해시 생성: ${newPasswordHash.take(30)}...")
                hectoFinancialClient.updatePassword(newPasswordHash)
                val savedClient = clientRepository.save(hectoFinancialClient)
                println("비밀번호 저장 완료 - 저장된 클라이언트 ID: ${savedClient.id}")
                
                // 저장 후 즉시 다시 조회하여 검증
                clientRepository.flush() // DB에 즉시 반영
                val verifiedClient = clientRepository.findByName("HectoFinancial")
                if (verifiedClient != null) {
                    val verificationResult = passwordEncoder.matches("password", verifiedClient.password)
                    if (verificationResult) {
                        println("✓ HectoFinancial 클라이언트의 비밀번호가 'password'로 업데이트되었습니다. (검증 완료)")
                    } else {
                        println("✗ 경고: HectoFinancial 클라이언트의 비밀번호 업데이트 후 검증에 실패했습니다.")
                        println("  저장된 해시: ${verifiedClient.password.take(30)}...")
                    }
                }
            } else {
                println("✓ HectoFinancial 클라이언트의 비밀번호가 이미 올바르게 설정되어 있습니다.")
            }
        } else {
            println("경고: HectoFinancial 클라이언트를 찾을 수 없습니다.")
        }
        
        // 기본 Client 생성 또는 조회 (주석 처리됨)
        // val defaultClient = clientRepository.findByName("HectoData")
        //     ?: clientRepository.save(
        //         Client(
        //             id = 0L,
        //             name = "HectoData",
        //             password = passwordEncoder.encode("password"), // BCrypt로 암호화
        //             description = "Default client for local testing",
        //             createdAt = LocalDateTime.now(),
        //             updatedAt = LocalDateTime.now(),
        //             deletedAt = null
        //         )
        //     )
        // Vendor.entries.forEach { vendor ->
        //     val quota = Quota.create(
        //         clientId = defaultClient.id,
        //         vendor = vendor,
        //         capacity = 100000000000000L,
        //         cycleUnit = CycleUnit.WEEKS,
        //     )
        //     quotaRepository.save(quota)
        // }
        //
        // createClaude(defaultClient)
        // createOpenAi(defaultClient)
        // createGemini(defaultClient)
        // createGrokApiKey(defaultClient)
    }

    private fun createClaude(client: Client) {
        val now = LocalDateTime.now()
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
        val existingMapping = clientApiKeyRepository.findByClientIdAndApiKeyId(
            client.id,
            anthropicKey.id
        )

        if (existingMapping == null) {
            clientApiKeyRepository.save(
                ClientApiKey(
                    id = 0L,
                    client = client,
                    apiKey = anthropicKey,
                    isActive = true,
                    createdAt = now,
                    updatedAt = now,
                    deletedAt = null
                )
            )
            println("Anthropic API Key mapped to client: ${client.name}")
        } else {
            println("Anthropic API Key already mapped to client: ${client.name}")
        }
    }

    private fun createOpenAi(client: Client) {
        val now = LocalDateTime.now()
        val openAIKey = apiKeyRepository.findByVendor(Vendor.OPENAI)
            ?: apiKeyRepository.save(
                OpenAiApiKey(
                    id = 0L,
                    vendor = Vendor.OPENAI,
                    description = "OpenAI API Key from application.yml",
                    createdAt = LocalDateTime.now(),
                    updatedAt = LocalDateTime.now(),
                    deletedAt = null,
                    apiKey = openAiApiKey
                )
            )

        val existingOpenAiMapping = clientApiKeyRepository.findByClientIdAndApiKeyId(
            client.id,
            openAIKey.id
        )

        if (existingOpenAiMapping == null) {
            clientApiKeyRepository.save(
                ClientApiKey(
                    id = 0L,
                    client = client,
                    apiKey = openAIKey,
                    isActive = true,
                    createdAt = now,
                    updatedAt = now,
                    deletedAt = null
                )
            )
        }
    }

    private fun createGemini(client: Client) {
        val now = LocalDateTime.now()
        val geminiKey = apiKeyRepository.findByVendor(Vendor.GOOGLE)
            ?: apiKeyRepository.save(
                GoogleApiKey(
                    id = 0L,
                    vendor = Vendor.GOOGLE,
                    description = "OpenAI API Key from application.yml",
                    createdAt = LocalDateTime.now(),
                    updatedAt = LocalDateTime.now(),
                    deletedAt = null,
                    apiKey = geminiApiKey,
                )
            )

        val existingOpenAiMapping = clientApiKeyRepository.findByClientIdAndApiKeyId(
            client.id,
            geminiKey.id
        )

        if (existingOpenAiMapping == null) {
            clientApiKeyRepository.save(
                ClientApiKey(
                    id = 0L,
                    client = client,
                    apiKey = geminiKey,
                    isActive = true,
                    createdAt = now,
                    updatedAt = now,
                    deletedAt = null
                )
            )
        }
    }
    private fun createGrokApiKey(client: Client) {
        val now = LocalDateTime.now()
        val grokKey = apiKeyRepository.findByVendor(Vendor.X_AI)
            ?: apiKeyRepository.save(
                XAiApiKey(
                    id = 0L,
                    vendor = Vendor.X_AI,
                    description = "OpenAI API Key from application.yml",
                    createdAt = LocalDateTime.now(),
                    updatedAt = LocalDateTime.now(),
                    deletedAt = null,
                    apiKey = grokApiKey,
                )
            )

        val existingOpenAiMapping = clientApiKeyRepository.findByClientIdAndApiKeyId(
            client.id,
            grokKey.id
        )

        if (existingOpenAiMapping == null) {
            clientApiKeyRepository.save(
                ClientApiKey(
                    id = 0L,
                    client = client,
                    apiKey = grokKey,
                    isActive = true,
                    createdAt = now,
                    updatedAt = now,
                    deletedAt = null
                )
            )
        }
    }
}

