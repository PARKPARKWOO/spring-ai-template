package com.example.demo.adapter.`in`.api

import com.example.demo.adapter.out.persistence.*
import com.example.demo.business.exception.ClientServiceException
import com.example.demo.common.annotation.CurrentClientId
import com.example.demo.common.constants.CycleUnit
import com.example.demo.dto.ApiResponse
import com.example.demo.model.*
import com.example.demo.model.api.*
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 백오피스 관리용 API
 * Client, ApiKey, Token 정책 등을 관리
 * 
 * Note: 이 컨트롤러는 Swagger UI에서 제외됩니다.
 * Swagger에서 보이지 않도록 OpenApiConfig에서 설정되어 있습니다.
 */
@RestController
@RequestMapping("/api/backoffice")
@Tag(name = "Backoffice", description = "백오피스 관리 API (Swagger에서 제외됨)")
@io.swagger.v3.oas.annotations.Hidden // Swagger에서 숨김 처리
class BackofficeController(
    private val clientRepository: ClientRepository,
    private val apiKeyRepository: ApiKeyRepository,
    private val clientApiKeyRepository: ClientApiKeyRepository,
    private val tokenPricingPolicyRepository: TokenPricingPolicyRepository,
    private val clientTokenQuotaRepository: ClientTokenQuotaRepository,
    private val clientPricingQuotaRepository: ClientPricingQuotaRepository,
    private val passwordEncoder: PasswordEncoder,
) {

    // ============================================
    // Client 관리
    // ============================================

    @GetMapping("/clients")
    @Operation(summary = "Client 목록 조회")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "appKeyAuth")
    fun getClients(
        @CurrentClientId clientId: Long,
        @io.swagger.v3.oas.annotations.Parameter(hidden = true)
        @RequestAttribute("clientRole") clientRole: String?
    ): ApiResponse<List<ClientInfo>> {
        val isSuperAdmin = clientRole == "SUPER_ADMIN"
        
        val clients = if (isSuperAdmin) {
            // SUPER_ADMIN: 모든 Client 조회
            clientRepository.findAll()
                .filter { it.deletedAt == null }
        } else {
            // 일반 CLIENT: 자신의 정보만 조회
            val client = clientRepository.findById(clientId)
                .filter { it.deletedAt == null }
                .orElse(null)
            if (client != null) listOf(client) else emptyList()
        }
        
        return ApiResponse.success(
            data = clients.map { ClientInfo.fromEntity(it) },
            message = if (isSuperAdmin) "모든 Client 목록을 조회했습니다." else "Client 정보를 조회했습니다."
        )
    }

    @PostMapping("/clients")
    @Operation(summary = "Client 생성")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "appKeyAuth")
    @Transactional
    fun createClient(@RequestBody request: CreateClientRequest): ApiResponse<ClientInfo> {
        // 이름 중복 확인
        val existing = clientRepository.findByName(request.name)
        if (existing != null && existing.deletedAt == null) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 존재하는 Client 이름입니다.")
        }

        val now = LocalDateTime.now()
        val client = Client(
            id = 0L,
            name = request.name,
            password = passwordEncoder.encode(request.password),
            description = request.description,
            role = request.role?.let { ClientRole.valueOf(it) } ?: ClientRole.CLIENT,
            createdAt = now,
            updatedAt = now,
            deletedAt = null
        )

        val saved = clientRepository.save(client)
        clientRepository.flush() // 즉시 DB에 반영
        
        return ApiResponse.success(data = ClientInfo.fromEntity(saved), message = "Client가 생성되었습니다.")
    }

    @PutMapping("/clients/{clientId}")
    @Operation(summary = "Client 수정")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "appKeyAuth")
    fun updateClient(
        @PathVariable clientId: Long,
        @RequestBody request: UpdateClientRequest
    ): ApiResponse<ClientInfo> {
        val client = clientRepository.findById(clientId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Client를 찾을 수 없습니다.") }

        if (client.deletedAt != null) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "삭제된 Client입니다.")
        }

        // 이름 중복 확인 (자기 자신 제외)
        request.name?.let { newName ->
            if (newName != client.name) {
                val existing = clientRepository.findByName(newName)
                if (existing != null && existing.id != clientId && existing.deletedAt == null) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 존재하는 Client 이름입니다.")
                }
            }
        }

        val updated = Client(
            id = client.id,
            name = request.name ?: client.name,
            password = request.password?.let { passwordEncoder.encode(it) } ?: client.password,
            description = request.description ?: client.description,
            role = request.role?.let { ClientRole.valueOf(it) } ?: client.role,
            createdAt = client.createdAt,
            updatedAt = LocalDateTime.now(),
            deletedAt = client.deletedAt
        )

        val saved = clientRepository.save(updated)
        return ApiResponse.success(data = ClientInfo.fromEntity(saved), message = "Client가 수정되었습니다.")
    }

    @GetMapping("/clients/{clientId}/api-keys")
    @Operation(summary = "Client에 연결된 ApiKey 목록 조회")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "appKeyAuth")
    fun getClientApiKeys(
        @PathVariable clientId: Long,
        @CurrentClientId currentClientId: Long,
        @io.swagger.v3.oas.annotations.Parameter(hidden = true)
        @RequestAttribute("clientRole") clientRole: String?
    ): ApiResponse<List<ApiKeyInfo>> {
        val isSuperAdmin = clientRole == "SUPER_ADMIN"
        
        // 권한 체크: SUPER_ADMIN이 아니면 자신의 정보만 조회 가능
        if (!isSuperAdmin && clientId != currentClientId) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "다른 Client의 정보를 조회할 권한이 없습니다.")
        }
        
        val client = clientRepository.findById(clientId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Client를 찾을 수 없습니다.") }

        if (client.deletedAt != null) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "삭제된 Client입니다.")
        }

        // fetch join을 사용하여 apiKey를 함께 로드
        val clientApiKeys = clientApiKeyRepository.findByClientIdWithApiKey(clientId)
        val apiKeys = clientApiKeys.map { it.apiKey }.map { ApiKeyInfo.fromEntity(it) }
        
        return ApiResponse.success(data = apiKeys, message = "Client에 연결된 ApiKey 목록을 조회했습니다.")
    }

    @PostMapping("/clients/{clientId}/api-keys/{apiKeyId}")
    @Operation(summary = "Client에 ApiKey 연결")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "appKeyAuth")
    @Transactional
    fun linkClientToApiKey(
        @PathVariable clientId: Long,
        @PathVariable apiKeyId: Long
    ): ApiResponse<Unit> {
        val client = clientRepository.findById(clientId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Client를 찾을 수 없습니다.") }

        val apiKey = apiKeyRepository.findById(apiKeyId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "ApiKey를 찾을 수 없습니다.") }

        if (apiKey.deletedAt != null || client.deletedAt != null) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "삭제된 ApiKey 또는 Client입니다.")
        }

        // 이미 연결되어 있는지 확인
        val existing = clientApiKeyRepository.findByClientIdAndApiKeyId(clientId, apiKeyId)
        if (existing != null && existing.deletedAt == null) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 연결된 ApiKey입니다.")
        }

        val now = LocalDateTime.now()
        val clientApiKey = ClientApiKey(
            id = 0L,
            client = client,
            apiKey = apiKey,
            isActive = true,
            createdAt = now,
            updatedAt = now,
            deletedAt = null
        )

        clientApiKeyRepository.save(clientApiKey)
        clientApiKeyRepository.flush()
        return ApiResponse.success(message = "ApiKey가 Client에 연결되었습니다.")
    }

    @DeleteMapping("/clients/{clientId}/api-keys/{apiKeyId}")
    @Operation(summary = "Client와 ApiKey 연결 해제")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "appKeyAuth")
    @Transactional
    fun unlinkClientFromApiKey(
        @PathVariable clientId: Long,
        @PathVariable apiKeyId: Long
    ): ApiResponse<Unit> {
        val clientApiKey = clientApiKeyRepository.findByClientIdAndApiKeyId(clientId, apiKeyId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "연결을 찾을 수 없습니다.")

        if (clientApiKey.deletedAt != null) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 해제된 연결입니다.")
        }

        val now = LocalDateTime.now()
        val deleted = ClientApiKey(
            id = clientApiKey.id,
            client = clientApiKey.client,
            apiKey = clientApiKey.apiKey,
            isActive = clientApiKey.isActive,
            createdAt = clientApiKey.createdAt,
            updatedAt = now,
            deletedAt = now
        )

        clientApiKeyRepository.save(deleted)
        clientApiKeyRepository.flush()
        return ApiResponse.success(message = "ApiKey와 Client 연결이 해제되었습니다.")
    }

    @DeleteMapping("/clients/{clientId}")
    @Operation(summary = "Client 삭제 (소프트 삭제)")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "appKeyAuth")
    fun deleteClient(@PathVariable clientId: Long): ApiResponse<Unit> {
        val client = clientRepository.findById(clientId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Client를 찾을 수 없습니다.") }

        if (client.deletedAt != null) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 삭제된 Client입니다.")
        }

        val deleted = Client(
            id = client.id,
            name = client.name,
            password = client.password,
            description = client.description,
            role = client.role,
            createdAt = client.createdAt,
            updatedAt = LocalDateTime.now(),
            deletedAt = LocalDateTime.now()
        )

        clientRepository.save(deleted)
        return ApiResponse.success(message = "Client가 삭제되었습니다.")
    }

    // ============================================
    // ApiKey 관리
    // ============================================

    @GetMapping("/api-keys")
    @Operation(summary = "ApiKey 목록 조회")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "appKeyAuth")
    fun getApiKeys(): ApiResponse<List<ApiKeyInfo>> {
        val apiKeys = apiKeyRepository.findAll()
            .filter { it.deletedAt == null }
            .map { ApiKeyInfo.fromEntity(it) }
        return ApiResponse.success(data = apiKeys, message = "ApiKey 목록을 조회했습니다.")
    }

    @PostMapping("/api-keys/openai")
    @Operation(summary = "OpenAI ApiKey 생성")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "appKeyAuth")
    fun createOpenAiApiKey(@RequestBody request: CreateApiKeyRequest): ApiResponse<ApiKeyInfo> {
        val now = LocalDateTime.now()
        val apiKey = OpenAiApiKey(
            id = 0L,
            vendor = Vendor.OPENAI,
            description = request.description,
            apiKey = request.apiKey,
            createdAt = now,
            updatedAt = now,
            deletedAt = null
        )

        val saved = apiKeyRepository.save(apiKey)
        return ApiResponse.success(data = ApiKeyInfo.fromEntity(saved), message = "OpenAI ApiKey가 생성되었습니다.")
    }

    @PostMapping("/api-keys/anthropic")
    @Operation(summary = "Anthropic ApiKey 생성")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "appKeyAuth")
    fun createAnthropicApiKey(@RequestBody request: CreateApiKeyRequest): ApiResponse<ApiKeyInfo> {
        val now = LocalDateTime.now()
        val apiKey = AnthropicApiKey(
            id = 0L,
            vendor = Vendor.ANTHROPIC,
            description = request.description,
            apiKey = request.apiKey,
            createdAt = now,
            updatedAt = now,
            deletedAt = null
        )

        val saved = apiKeyRepository.save(apiKey)
        return ApiResponse.success(data = ApiKeyInfo.fromEntity(saved), message = "Anthropic ApiKey가 생성되었습니다.")
    }

    @PostMapping("/api-keys/google")
    @Operation(summary = "Google ApiKey 생성")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "appKeyAuth")
    fun createGoogleApiKey(@RequestBody request: CreateGoogleApiKeyRequest): ApiResponse<ApiKeyInfo> {
        val now = LocalDateTime.now()
        val apiKey = GoogleApiKey(
            id = 0L,
            vendor = Vendor.GOOGLE,
            description = request.description,
            apiKey = request.apiKey,
            projectId = request.projectId,
            location = request.location,
            createdAt = now,
            updatedAt = now,
            deletedAt = null
        )

        val saved = apiKeyRepository.save(apiKey)
        return ApiResponse.success(data = ApiKeyInfo.fromEntity(saved), message = "Google ApiKey가 생성되었습니다.")
    }

    @PostMapping("/api-keys/xai")
    @Operation(summary = "xAI ApiKey 생성")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "appKeyAuth")
    fun createXAiApiKey(@RequestBody request: CreateApiKeyRequest): ApiResponse<ApiKeyInfo> {
        val now = LocalDateTime.now()
        val apiKey = XAiApiKey(
            id = 0L,
            vendor = Vendor.X_AI,
            description = request.description,
            apiKey = request.apiKey,
            createdAt = now,
            updatedAt = now,
            deletedAt = null
        )

        val saved = apiKeyRepository.save(apiKey)
        return ApiResponse.success(data = ApiKeyInfo.fromEntity(saved), message = "xAI ApiKey가 생성되었습니다.")
    }

    @PutMapping("/api-keys/{apiKeyId}")
    @Operation(summary = "ApiKey 수정")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "appKeyAuth")
    fun updateApiKey(
        @PathVariable apiKeyId: Long,
        @RequestBody request: UpdateApiKeyRequest
    ): ApiResponse<ApiKeyInfo> {
        val apiKey = apiKeyRepository.findById(apiKeyId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "ApiKey를 찾을 수 없습니다.") }

        if (apiKey.deletedAt != null) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "삭제된 ApiKey입니다.")
        }

        // 벤더별로 적절한 타입으로 업데이트
        val updated = when (apiKey.vendor) {
            Vendor.OPENAI -> {
                val openAiKey = apiKey as OpenAiApiKey
                OpenAiApiKey(
                    id = openAiKey.id,
                    vendor = openAiKey.vendor,
                    description = request.description ?: openAiKey.description,
                    apiKey = request.apiKey ?: openAiKey.apiKey,
                    createdAt = openAiKey.createdAt,
                    updatedAt = LocalDateTime.now(),
                    deletedAt = openAiKey.deletedAt
                )
            }
            Vendor.ANTHROPIC -> {
                val anthropicKey = apiKey as AnthropicApiKey
                AnthropicApiKey(
                    id = anthropicKey.id,
                    vendor = anthropicKey.vendor,
                    description = request.description ?: anthropicKey.description,
                    apiKey = request.apiKey ?: anthropicKey.apiKey,
                    createdAt = anthropicKey.createdAt,
                    updatedAt = LocalDateTime.now(),
                    deletedAt = anthropicKey.deletedAt
                )
            }
            Vendor.GOOGLE -> {
                val googleKey = apiKey as GoogleApiKey
                GoogleApiKey(
                    id = googleKey.id,
                    vendor = googleKey.vendor,
                    description = request.description ?: googleKey.description,
                    apiKey = request.apiKey ?: googleKey.apiKey,
                    projectId = request.projectId ?: googleKey.projectId,
                    location = request.location ?: googleKey.location,
                    createdAt = googleKey.createdAt,
                    updatedAt = LocalDateTime.now(),
                    deletedAt = googleKey.deletedAt
                )
            }
            Vendor.X_AI -> {
                val xAiKey = apiKey as XAiApiKey
                XAiApiKey(
                    id = xAiKey.id,
                    vendor = xAiKey.vendor,
                    description = request.description ?: xAiKey.description,
                    apiKey = request.apiKey ?: xAiKey.apiKey,
                    createdAt = xAiKey.createdAt,
                    updatedAt = LocalDateTime.now(),
                    deletedAt = xAiKey.deletedAt
                )
            }
        }

        val saved = apiKeyRepository.save(updated)
        return ApiResponse.success(data = ApiKeyInfo.fromEntity(saved), message = "ApiKey가 수정되었습니다.")
    }

    @DeleteMapping("/api-keys/{apiKeyId}")
    @Operation(summary = "ApiKey 삭제 (소프트 삭제)")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "appKeyAuth")
    fun deleteApiKey(@PathVariable apiKeyId: Long): ApiResponse<Unit> {
        val apiKey = apiKeyRepository.findById(apiKeyId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "ApiKey를 찾을 수 없습니다.") }

        if (apiKey.deletedAt != null) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 삭제된 ApiKey입니다.")
        }

        // 벤더별로 적절한 타입으로 삭제 처리
        val deleted = when (apiKey.vendor) {
            Vendor.OPENAI -> {
                val openAiKey = apiKey as OpenAiApiKey
                OpenAiApiKey(
                    id = openAiKey.id,
                    vendor = openAiKey.vendor,
                    description = openAiKey.description,
                    apiKey = openAiKey.apiKey,
                    createdAt = openAiKey.createdAt,
                    updatedAt = LocalDateTime.now(),
                    deletedAt = LocalDateTime.now()
                )
            }
            Vendor.ANTHROPIC -> {
                val anthropicKey = apiKey as AnthropicApiKey
                AnthropicApiKey(
                    id = anthropicKey.id,
                    vendor = anthropicKey.vendor,
                    description = anthropicKey.description,
                    apiKey = anthropicKey.apiKey,
                    createdAt = anthropicKey.createdAt,
                    updatedAt = LocalDateTime.now(),
                    deletedAt = LocalDateTime.now()
                )
            }
            Vendor.GOOGLE -> {
                val googleKey = apiKey as GoogleApiKey
                GoogleApiKey(
                    id = googleKey.id,
                    vendor = googleKey.vendor,
                    description = googleKey.description,
                    apiKey = googleKey.apiKey,
                    projectId = googleKey.projectId,
                    location = googleKey.location,
                    createdAt = googleKey.createdAt,
                    updatedAt = LocalDateTime.now(),
                    deletedAt = LocalDateTime.now()
                )
            }
            Vendor.X_AI -> {
                val xAiKey = apiKey as XAiApiKey
                XAiApiKey(
                    id = xAiKey.id,
                    vendor = xAiKey.vendor,
                    description = xAiKey.description,
                    apiKey = xAiKey.apiKey,
                    createdAt = xAiKey.createdAt,
                    updatedAt = LocalDateTime.now(),
                    deletedAt = LocalDateTime.now()
                )
            }
        }

        apiKeyRepository.save(deleted)
        return ApiResponse.success(message = "ApiKey가 삭제되었습니다.")
    }

    @GetMapping("/api-keys/{apiKeyId}/clients")
    @Operation(summary = "ApiKey에 연결된 Client 목록 조회")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "appKeyAuth")
    fun getApiKeyClients(@PathVariable apiKeyId: Long): ApiResponse<List<ClientInfo>> {
        // fetch join을 사용하여 client를 함께 로드
        val clientApiKeys = clientApiKeyRepository.findByApiKeyIdWithClient(apiKeyId)
        val clients = clientApiKeys.map { it.client }.map { ClientInfo.fromEntity(it) }
        
        return ApiResponse.success(data = clients, message = "ApiKey에 연결된 Client 목록을 조회했습니다.")
    }

    @PostMapping("/api-keys/{apiKeyId}/clients/{clientId}")
    @Operation(summary = "ApiKey를 Client에 연결")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "appKeyAuth")
    fun linkApiKeyToClient(
        @PathVariable apiKeyId: Long,
        @PathVariable clientId: Long
    ): ApiResponse<Unit> {
        val apiKey = apiKeyRepository.findById(apiKeyId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "ApiKey를 찾을 수 없습니다.") }

        val client = clientRepository.findById(clientId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Client를 찾을 수 없습니다.") }

        if (apiKey.deletedAt != null || client.deletedAt != null) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "삭제된 ApiKey 또는 Client입니다.")
        }

        // 이미 연결되어 있는지 확인
        val existing = clientApiKeyRepository.findByClientIdAndApiKeyId(clientId, apiKeyId)
        if (existing != null && existing.deletedAt == null) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 연결된 ApiKey입니다.")
        }

        val now = LocalDateTime.now()
        val clientApiKey = ClientApiKey(
            id = 0L,
            client = client,
            apiKey = apiKey,
            isActive = true,
            createdAt = now,
            updatedAt = now,
            deletedAt = null
        )

        clientApiKeyRepository.save(clientApiKey)
        return ApiResponse.success(message = "ApiKey가 Client에 연결되었습니다.")
    }

    @DeleteMapping("/api-keys/{apiKeyId}/clients/{clientId}")
    @Operation(summary = "ApiKey와 Client 연결 해제")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "appKeyAuth")
    fun unlinkApiKeyFromClient(
        @PathVariable apiKeyId: Long,
        @PathVariable clientId: Long
    ): ApiResponse<Unit> {
        val clientApiKey = clientApiKeyRepository.findByClientIdAndApiKeyId(clientId, apiKeyId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "연결을 찾을 수 없습니다.")

        if (clientApiKey.deletedAt != null) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 해제된 연결입니다.")
        }

        val now = LocalDateTime.now()
        val deleted = ClientApiKey(
            id = clientApiKey.id,
            client = clientApiKey.client,
            apiKey = clientApiKey.apiKey,
            isActive = clientApiKey.isActive,
            createdAt = clientApiKey.createdAt,
            updatedAt = now,
            deletedAt = now
        )

        clientApiKeyRepository.save(deleted)
        return ApiResponse.success(message = "ApiKey와 Client 연결이 해제되었습니다.")
    }

    // ============================================
    // Token Pricing Policy 관리
    // ============================================

    @GetMapping("/token-pricing-policies")
    @Operation(summary = "Token Pricing Policy 목록 조회")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "appKeyAuth")
    fun getTokenPricingPolicies(): ApiResponse<List<TokenPricingPolicyInfo>> {
        val policies = tokenPricingPolicyRepository.findAll()
            .filter { it.getDeletedAt() == null }
            .map { TokenPricingPolicyInfo.fromEntity(it) }
        return ApiResponse.success(data = policies, message = "Token Pricing Policy 목록을 조회했습니다.")
    }

    @PostMapping("/token-pricing-policies")
    @Operation(summary = "Token Pricing Policy 생성 (공통 정책)")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "appKeyAuth")
    fun createTokenPricingPolicy(@RequestBody request: CreateTokenPricingPolicyRequest): ApiResponse<TokenPricingPolicyInfo> {
        // 중복 확인 (공통 정책: clientId가 null)
        val existing = tokenPricingPolicyRepository.findByVendorAndModelAndClientIdIsNullAndIsActiveTrueAndDeletedAtIsNull(
            request.vendor, request.model
        )
        if (existing != null) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 존재하는 공통 가격 정책입니다.")
        }
        
        val policy = TokenPricingPolicy.create(
            vendor = request.vendor,
            model = request.model,
            inputTokenPricePerMillion = request.inputTokenPricePerMillion,
            outputTokenPricePerMillion = request.outputTokenPricePerMillion
        )

        val saved = tokenPricingPolicyRepository.save(policy)
        return ApiResponse.success(data = TokenPricingPolicyInfo.fromEntity(saved), message = "공통 Token Pricing Policy가 생성되었습니다.")
    }
    
    @PostMapping("/clients/{clientId}/token-pricing-policies")
    @Operation(summary = "Client별 Token Pricing Policy 생성")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "appKeyAuth")
    fun createClientTokenPricingPolicy(
        @PathVariable clientId: Long,
        @RequestBody request: CreateTokenPricingPolicyRequest
    ): ApiResponse<TokenPricingPolicyInfo> {
        // Client 존재 확인
        val client = clientRepository.findById(clientId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Client를 찾을 수 없습니다.") }
        
        if (client.deletedAt != null) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "삭제된 Client입니다.")
        }
        
        // 중복 확인 (Client별 정책)
        val existing = tokenPricingPolicyRepository.findByClientIdAndVendorAndModelAndIsActiveTrueAndDeletedAtIsNull(
            clientId, request.vendor, request.model
        )
        if (existing != null) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 존재하는 Client별 가격 정책입니다.")
        }
        
        val policy = TokenPricingPolicy.createForClient(
            clientId = clientId,
            vendor = request.vendor,
            model = request.model,
            inputTokenPricePerMillion = request.inputTokenPricePerMillion,
            outputTokenPricePerMillion = request.outputTokenPricePerMillion
        )

        val saved = tokenPricingPolicyRepository.save(policy)
        return ApiResponse.success(data = TokenPricingPolicyInfo.fromEntity(saved), message = "Client별 Token Pricing Policy가 생성되었습니다.")
    }
    
    @GetMapping("/clients/{clientId}/token-pricing-policies")
    @Operation(summary = "Client별 Token Pricing Policy 목록 조회")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "appKeyAuth")
    fun getClientTokenPricingPolicies(
        @PathVariable clientId: Long,
        @CurrentClientId currentClientId: Long,
        @io.swagger.v3.oas.annotations.Parameter(hidden = true)
        @RequestAttribute("clientRole") clientRole: String?
    ): ApiResponse<List<TokenPricingPolicyInfo>> {
        val isSuperAdmin = clientRole == "SUPER_ADMIN"
        
        // 권한 체크: SUPER_ADMIN이 아니면 자신의 정보만 조회 가능
        if (!isSuperAdmin && clientId != currentClientId) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "다른 Client의 정보를 조회할 권한이 없습니다.")
        }
        
        // Client 존재 확인
        val client = clientRepository.findById(clientId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Client를 찾을 수 없습니다.") }
        
        if (client.deletedAt != null) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "삭제된 Client입니다.")
        }
        
        val policies = tokenPricingPolicyRepository.findByClientIdAndIsActiveTrueAndDeletedAtIsNull(clientId)
            .map { TokenPricingPolicyInfo.fromEntity(it) }
        return ApiResponse.success(data = policies, message = "Client별 Token Pricing Policy 목록을 조회했습니다.")
    }

    @PutMapping("/token-pricing-policies/{policyId}")
    @Operation(summary = "Token Pricing Policy 수정")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "appKeyAuth")
    fun updateTokenPricingPolicy(
        @PathVariable policyId: Long,
        @RequestBody request: UpdateTokenPricingPolicyRequest
    ): ApiResponse<TokenPricingPolicyInfo> {
        val policy = tokenPricingPolicyRepository.findById(policyId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Token Pricing Policy를 찾을 수 없습니다.") }

        if (policy.getDeletedAt() != null) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "삭제된 Token Pricing Policy입니다.")
        }

        // TODO: Entity에 수정 메서드 추가 필요 (현재는 불변 객체)
        // 임시로 새로 생성
        val updated = TokenPricingPolicy(
            id = policy.getId(),
            vendor = request.vendor ?: policy.getVendor(),
            model = request.model ?: policy.getModel(),
            clientId = policy.getClientId(), // clientId는 수정 불가
            inputTokenPricePerMillion = request.inputTokenPricePerMillion ?: policy.getInputTokenPricePerMillion(),
            outputTokenPricePerMillion = request.outputTokenPricePerMillion ?: policy.getOutputTokenPricePerMillion(),
            isActive = request.isActive ?: policy.getIsActive(),
            createdAt = policy.getCreatedAt(),
            updatedAt = LocalDateTime.now(),
            deletedAt = policy.getDeletedAt()
        )

        val saved = tokenPricingPolicyRepository.save(updated)
        return ApiResponse.success(data = TokenPricingPolicyInfo.fromEntity(saved), message = "Token Pricing Policy가 수정되었습니다.")
    }

    @DeleteMapping("/token-pricing-policies/{policyId}")
    @Operation(summary = "Token Pricing Policy 삭제 (소프트 삭제)")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "appKeyAuth")
    fun deleteTokenPricingPolicy(@PathVariable policyId: Long): ApiResponse<Unit> {
        val policy = tokenPricingPolicyRepository.findById(policyId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Token Pricing Policy를 찾을 수 없습니다.") }

        if (policy.getDeletedAt() != null) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 삭제된 Token Pricing Policy입니다.")
        }

        val deleted = TokenPricingPolicy(
            id = policy.getId(),
            vendor = policy.getVendor(),
            model = policy.getModel(),
            clientId = policy.getClientId(),
            inputTokenPricePerMillion = policy.getInputTokenPricePerMillion(),
            outputTokenPricePerMillion = policy.getOutputTokenPricePerMillion(),
            isActive = policy.getIsActive(),
            createdAt = policy.getCreatedAt(),
            updatedAt = LocalDateTime.now(),
            deletedAt = LocalDateTime.now()
        )

        tokenPricingPolicyRepository.save(deleted)
        return ApiResponse.success(message = "Token Pricing Policy가 삭제되었습니다.")
    }

    // ============================================
    // Client Token Quota 관리
    // ============================================

    @GetMapping("/clients/{clientId}/token-quotas")
    @Operation(summary = "Client Token Quota 목록 조회")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "appKeyAuth")
    fun getClientTokenQuotas(
        @PathVariable clientId: Long,
        @CurrentClientId currentClientId: Long,
        @io.swagger.v3.oas.annotations.Parameter(hidden = true)
        @RequestAttribute("clientRole") clientRole: String?
    ): ApiResponse<List<ClientTokenQuotaInfo>> {
        val isSuperAdmin = clientRole == "SUPER_ADMIN"
        
        // 권한 체크: SUPER_ADMIN이 아니면 자신의 정보만 조회 가능
        if (!isSuperAdmin && clientId != currentClientId) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "다른 Client의 정보를 조회할 권한이 없습니다.")
        }
        
        val quotas = clientTokenQuotaRepository.findAll()
            .filter { it.getClientId() == clientId && it.getDeletedAt() == null }
            .map { ClientTokenQuotaInfo.fromEntity(it) }
        return ApiResponse.success(data = quotas, message = "Client Token Quota 목록을 조회했습니다.")
    }

    @PostMapping("/clients/{clientId}/token-quotas")
    @Operation(summary = "Client Token Quota 생성")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "appKeyAuth")
    fun createClientTokenQuota(
        @PathVariable clientId: Long,
        @RequestBody request: CreateClientTokenQuotaRequest
    ): ApiResponse<ClientTokenQuotaInfo> {
        val client = clientRepository.findById(clientId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Client를 찾을 수 없습니다.") }

        if (client.deletedAt != null) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "삭제된 Client입니다.")
        }

        val quota = ClientTokenQuota.create(
            clientId = clientId,
            maxTokens = request.maxTokens,
            cycleUnit = request.cycleUnit,
            vendor = request.vendor,
            model = request.model
        )

        val saved = clientTokenQuotaRepository.save(quota)
        return ApiResponse.success(data = ClientTokenQuotaInfo.fromEntity(saved), message = "Client Token Quota가 생성되었습니다.")
    }

    @DeleteMapping("/token-quotas/{quotaId}")
    @Operation(summary = "Client Token Quota 삭제 (소프트 삭제)")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "appKeyAuth")
    fun deleteClientTokenQuota(@PathVariable quotaId: Long): ApiResponse<Unit> {
        val quota = clientTokenQuotaRepository.findById(quotaId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Client Token Quota를 찾을 수 없습니다.") }

        if (quota.getDeletedAt() != null) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 삭제된 Client Token Quota입니다.")
        }

        // TODO: Entity에 수정 메서드 추가 필요
        val deleted = ClientTokenQuota(
            id = quota.getId(),
            clientId = quota.getClientId(),
            vendor = quota.getVendor(),
            model = quota.getModel(),
            maxTokens = quota.getMaxTokens(),
            currentTokens = quota.getCurrentTokens(),
            cycleUnit = quota.getCycleUnit(),
            cycleStartedAt = quota.getCycleStartedAt(),
            nextResetAt = quota.getNextResetAt(),
            isActive = quota.getIsActive(),
            createdAt = quota.getCreatedAt(),
            updatedAt = LocalDateTime.now(),
            deletedAt = LocalDateTime.now()
        )

        clientTokenQuotaRepository.save(deleted)
        return ApiResponse.success(message = "Client Token Quota가 삭제되었습니다.")
    }

    // ============================================
    // Client Pricing Quota 관리
    // ============================================

    @GetMapping("/clients/{clientId}/pricing-quotas")
    @Operation(summary = "Client Pricing Quota 목록 조회")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "appKeyAuth")
    fun getClientPricingQuotas(
        @PathVariable clientId: Long,
        @CurrentClientId currentClientId: Long,
        @io.swagger.v3.oas.annotations.Parameter(hidden = true)
        @RequestAttribute("clientRole") clientRole: String?
    ): ApiResponse<List<ClientPricingQuotaInfo>> {
        val isSuperAdmin = clientRole == "SUPER_ADMIN"
        
        // 권한 체크: SUPER_ADMIN이 아니면 자신의 정보만 조회 가능
        if (!isSuperAdmin && clientId != currentClientId) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "다른 Client의 정보를 조회할 권한이 없습니다.")
        }
        
        val quotas = clientPricingQuotaRepository.findAll()
            .filter { it.getClientId() == clientId && it.getDeletedAt() == null }
            .map { ClientPricingQuotaInfo.fromEntity(it) }
        return ApiResponse.success(data = quotas, message = "Client Pricing Quota 목록을 조회했습니다.")
    }

    @PostMapping("/clients/{clientId}/pricing-quotas")
    @Operation(summary = "Client Pricing Quota 생성")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "appKeyAuth")
    fun createClientPricingQuota(
        @PathVariable clientId: Long,
        @RequestBody request: CreateClientPricingQuotaRequest
    ): ApiResponse<ClientPricingQuotaInfo> {
        val client = clientRepository.findById(clientId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Client를 찾을 수 없습니다.") }

        if (client.deletedAt != null) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "삭제된 Client입니다.")
        }

        val quota = ClientPricingQuota.create(
            clientId = clientId,
            maxAmount = request.maxAmount,
            cycleUnit = request.cycleUnit
        )

        val saved = clientPricingQuotaRepository.save(quota)
        return ApiResponse.success(data = ClientPricingQuotaInfo.fromEntity(saved), message = "Client Pricing Quota가 생성되었습니다.")
    }

    @DeleteMapping("/pricing-quotas/{quotaId}")
    @Operation(summary = "Client Pricing Quota 삭제 (소프트 삭제)")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "appKeyAuth")
    fun deleteClientPricingQuota(@PathVariable quotaId: Long): ApiResponse<Unit> {
        val quota = clientPricingQuotaRepository.findById(quotaId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Client Pricing Quota를 찾을 수 없습니다.") }

        if (quota.getDeletedAt() != null) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 삭제된 Client Pricing Quota입니다.")
        }

        // TODO: Entity에 수정 메서드 추가 필요
        val deleted = ClientPricingQuota(
            id = quota.getId(),
            clientId = quota.getClientId(),
            maxAmount = quota.getMaxAmount(),
            currentAmount = quota.getCurrentAmount(),
            cycleUnit = quota.getCycleUnit(),
            cycleStartedAt = quota.getCycleStartedAt(),
            nextResetAt = quota.getNextResetAt(),
            isActive = quota.getIsActive(),
            createdAt = quota.getCreatedAt(),
            updatedAt = LocalDateTime.now(),
            deletedAt = LocalDateTime.now()
        )

        clientPricingQuotaRepository.save(deleted)
        return ApiResponse.success(message = "Client Pricing Quota가 삭제되었습니다.")
    }

    // ============================================
    // DTO 정의
    // ============================================

    data class ClientInfo(
        val id: Long,
        val name: String,
        val description: String?,
        val role: String,
        val createdAt: String,
        val updatedAt: String,
    ) {
        companion object {
            fun fromEntity(client: Client): ClientInfo {
                return ClientInfo(
                    id = client.id,
                    name = client.name,
                    description = client.description,
                    role = client.role.name,
                    createdAt = client.createdAt.toString(),
                    updatedAt = client.updatedAt.toString()
                )
            }
        }
    }

    data class CreateClientRequest(
        val name: String,
        val password: String,
        val description: String? = null,
        val role: String? = null // "CLIENT" 또는 "SUPER_ADMIN", 기본값: "CLIENT"
    )

    data class UpdateClientRequest(
        val name: String? = null,
        val password: String? = null,
        val description: String? = null,
        val role: String? = null // "CLIENT" 또는 "SUPER_ADMIN"
    )

    data class ApiKeyInfo(
        val id: Long,
        val vendor: String,
        val description: String,
        val apiKey: String, // 실제 키 값 표시 (보안 주의)
        val projectId: String? = null,
        val location: String? = null,
        val createdAt: String,
        val updatedAt: String,
    ) {
        companion object {
            fun fromEntity(apiKey: ApiKey): ApiKeyInfo {
                return when (apiKey) {
                    is OpenAiApiKey -> ApiKeyInfo(
                        id = apiKey.id,
                        vendor = apiKey.vendor.name,
                        description = apiKey.description,
                        apiKey = apiKey.apiKey,
                        projectId = null,
                        location = null,
                        createdAt = apiKey.createdAt.toString(),
                        updatedAt = apiKey.updatedAt.toString()
                    )
                    is AnthropicApiKey -> ApiKeyInfo(
                        id = apiKey.id,
                        vendor = apiKey.vendor.name,
                        description = apiKey.description,
                        apiKey = apiKey.apiKey,
                        projectId = null,
                        location = null,
                        createdAt = apiKey.createdAt.toString(),
                        updatedAt = apiKey.updatedAt.toString()
                    )
                    is GoogleApiKey -> ApiKeyInfo(
                        id = apiKey.id,
                        vendor = apiKey.vendor.name,
                        description = apiKey.description,
                        apiKey = apiKey.apiKey,
                        projectId = apiKey.projectId,
                        location = apiKey.location,
                        createdAt = apiKey.createdAt.toString(),
                        updatedAt = apiKey.updatedAt.toString()
                    )
                    is XAiApiKey -> ApiKeyInfo(
                        id = apiKey.id,
                        vendor = apiKey.vendor.name,
                        description = apiKey.description,
                        apiKey = apiKey.apiKey,
                        projectId = null,
                        location = null,
                        createdAt = apiKey.createdAt.toString(),
                        updatedAt = apiKey.updatedAt.toString()
                    )
                    else -> throw IllegalArgumentException("Unknown ApiKey type")
                }
            }
        }
    }

    data class CreateApiKeyRequest(
        val apiKey: String,
        val description: String
    )

    data class CreateGoogleApiKeyRequest(
        val apiKey: String,
        val description: String,
        val projectId: String? = null,
        val location: String? = null
    )

    data class UpdateApiKeyRequest(
        val apiKey: String? = null,
        val description: String? = null,
        val projectId: String? = null, // Google 전용
        val location: String? = null // Google 전용
    )

    data class TokenPricingPolicyInfo(
        val id: Long,
        val vendor: String,
        val model: String,
        val clientId: Long?, // null이면 공통 정책, 값이 있으면 Client별 정책
        val inputTokenPricePerMillion: BigDecimal,
        val outputTokenPricePerMillion: BigDecimal,
        val isActive: Boolean,
        val createdAt: String,
        val updatedAt: String,
    ) {
        companion object {
            fun fromEntity(policy: TokenPricingPolicy): TokenPricingPolicyInfo {
                return TokenPricingPolicyInfo(
                    id = policy.getId(),
                    vendor = policy.getVendor().name,
                    model = policy.getModel(),
                    clientId = policy.getClientId(),
                    inputTokenPricePerMillion = policy.getInputTokenPricePerMillion(),
                    outputTokenPricePerMillion = policy.getOutputTokenPricePerMillion(),
                    isActive = policy.getIsActive(),
                    createdAt = policy.getCreatedAt().toString(),
                    updatedAt = policy.getUpdatedAt().toString()
                )
            }
        }
    }

    data class CreateTokenPricingPolicyRequest(
        val vendor: Vendor,
        val model: String,
        val inputTokenPricePerMillion: BigDecimal,
        val outputTokenPricePerMillion: BigDecimal
    )

    data class UpdateTokenPricingPolicyRequest(
        val vendor: Vendor? = null,
        val model: String? = null,
        val inputTokenPricePerMillion: BigDecimal? = null,
        val outputTokenPricePerMillion: BigDecimal? = null,
        val isActive: Boolean? = null
    )

    data class ClientTokenQuotaInfo(
        val id: Long,
        val clientId: Long,
        val vendor: String?,
        val model: String?,
        val maxTokens: Long,
        val currentTokens: Long,
        val cycleUnit: String,
        val cycleStartedAt: String,
        val nextResetAt: String,
        val isActive: Boolean,
        val createdAt: String,
        val updatedAt: String,
    ) {
        companion object {
            fun fromEntity(quota: ClientTokenQuota): ClientTokenQuotaInfo {
                return ClientTokenQuotaInfo(
                    id = quota.getId(),
                    clientId = quota.getClientId(),
                    vendor = quota.getVendor()?.name,
                    model = quota.getModel(),
                    maxTokens = quota.getMaxTokens(),
                    currentTokens = quota.getCurrentTokens(),
                    cycleUnit = quota.getCycleUnit().name,
                    cycleStartedAt = quota.getCycleStartedAt().toString(),
                    nextResetAt = quota.getNextResetAt().toString(),
                    isActive = quota.getIsActive(),
                    createdAt = quota.getCreatedAt().toString(),
                    updatedAt = quota.getUpdatedAt().toString()
                )
            }
        }
    }

    data class CreateClientTokenQuotaRequest(
        val maxTokens: Long,
        val cycleUnit: CycleUnit,
        val vendor: Vendor? = null,
        val model: String? = null
    )

    data class ClientPricingQuotaInfo(
        val id: Long,
        val clientId: Long,
        val maxAmount: BigDecimal,
        val currentAmount: BigDecimal,
        val cycleUnit: String,
        val cycleStartedAt: String,
        val nextResetAt: String,
        val isActive: Boolean,
        val createdAt: String,
        val updatedAt: String,
    ) {
        companion object {
            fun fromEntity(quota: ClientPricingQuota): ClientPricingQuotaInfo {
                return ClientPricingQuotaInfo(
                    id = quota.getId(),
                    clientId = quota.getClientId(),
                    maxAmount = quota.getMaxAmount(),
                    currentAmount = quota.getCurrentAmount(),
                    cycleUnit = quota.getCycleUnit().name,
                    cycleStartedAt = quota.getCycleStartedAt().toString(),
                    nextResetAt = quota.getNextResetAt().toString(),
                    isActive = quota.getIsActive(),
                    createdAt = quota.getCreatedAt().toString(),
                    updatedAt = quota.getUpdatedAt().toString()
                )
            }
        }
    }

    data class CreateClientPricingQuotaRequest(
        val maxAmount: BigDecimal,
        val cycleUnit: CycleUnit
    )
}
