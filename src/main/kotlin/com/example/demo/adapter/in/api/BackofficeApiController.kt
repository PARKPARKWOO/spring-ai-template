package com.example.demo.adapter.`in`.api

import com.example.demo.adapter.out.persistence.ApiKeyRepository
import com.example.demo.adapter.out.persistence.ClientPricingQuotaRepository
import com.example.demo.adapter.out.persistence.ClientRepository
import com.example.demo.adapter.out.persistence.ClientTokenQuotaRepository
import com.example.demo.adapter.out.persistence.TokenPricingPolicyRepository
import com.example.demo.business.AppKeyService
import com.example.demo.common.ratelimit.RateLimitPolicyService
import com.example.demo.dto.ApiResponse
import com.example.demo.model.ApiKeyTier
import com.example.demo.model.Client
import com.example.demo.model.TokenPricingPolicy
import com.example.demo.model.Vendor
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/backoffice")
@Tag(name = "Backoffice API", description = "Backoffice 관리 API (internal only)")
class BackofficeApiController(
    private val clientRepository: ClientRepository,
    private val tokenPricingPolicyRepository: TokenPricingPolicyRepository,
    private val clientPricingQuotaRepository: ClientPricingQuotaRepository,
    private val clientTokenQuotaRepository: ClientTokenQuotaRepository,
    private val appKeyService: AppKeyService,
    private val passwordEncoder: PasswordEncoder,
    private val apiKeyRepository: ApiKeyRepository,
    private val rateLimitPolicyService: RateLimitPolicyService,
) {
    // === Client Management ===

    @GetMapping("/clients")
    @Operation(summary = "클라이언트 목록 조회")
    fun listClients(): ResponseEntity<ApiResponse<List<Client>>> {
        val clients = clientRepository.findAll()
        return ResponseEntity.ok(ApiResponse.success(data = clients))
    }

    @GetMapping("/clients/{id}")
    @Operation(summary = "클라이언트 상세 조회")
    fun getClient(@PathVariable id: Long): ResponseEntity<ApiResponse<Client>> {
        val client = clientRepository.findById(id).orElseThrow { NoSuchElementException("Client not found: $id") }
        return ResponseEntity.ok(ApiResponse.success(data = client))
    }

    @PostMapping("/clients")
    @Operation(summary = "클라이언트 생성")
    fun createClient(@RequestBody request: CreateClientRequest): ResponseEntity<ApiResponse<Client>> {
        val now = LocalDateTime.now()
        val client = Client(
            name = request.name,
            password = passwordEncoder.encode(request.password),
            description = request.description,
            createdAt = now,
            updatedAt = now,
        )
        val saved = clientRepository.save(client)
        return ResponseEntity.ok(ApiResponse.success(data = saved, message = "클라이언트가 생성되었습니다."))
    }

    @DeleteMapping("/clients/{id}")
    @Operation(summary = "클라이언트 삭제")
    fun deleteClient(@PathVariable id: Long): ResponseEntity<ApiResponse<Unit>> {
        clientRepository.deleteById(id)
        return ResponseEntity.ok(ApiResponse.success(message = "클라이언트가 삭제되었습니다."))
    }

    // === App Key Management ===

    @GetMapping("/clients/{clientId}/app-keys")
    @Operation(summary = "클라이언트의 AppKey 목록 조회")
    fun listAppKeys(@PathVariable clientId: Long): ResponseEntity<ApiResponse<Any>> {
        val keys = appKeyService.getAppKeysByClientId(clientId)
        return ResponseEntity.ok(ApiResponse.success(data = keys))
    }

    @PostMapping("/clients/{clientId}/app-keys")
    @Operation(summary = "AppKey 발급")
    fun issueAppKey(
        @PathVariable clientId: Long,
        @RequestBody request: IssueAppKeyRequest,
    ): ResponseEntity<ApiResponse<Map<String, Any>>> {
        val (originalKey, appKey) = appKeyService.issueAppKey(clientId, request.name, request.description)
        return ResponseEntity.ok(
            ApiResponse.success(
                data = mapOf("key" to originalKey, "id" to appKey.id),
                message = "AppKey가 발급되었습니다. 이 키는 다시 확인할 수 없으니 안전하게 보관하세요.",
            )
        )
    }

    @PostMapping("/app-keys/{appKeyId}/deactivate")
    @Operation(summary = "AppKey 비활성화")
    fun deactivateAppKey(
        @PathVariable appKeyId: Long,
        @RequestParam clientId: Long,
    ): ResponseEntity<ApiResponse<Unit>> {
        appKeyService.deactivateAppKey(appKeyId, clientId)
        return ResponseEntity.ok(ApiResponse.success(message = "AppKey가 비활성화되었습니다."))
    }

    // === API Key Management (all) ===

    @GetMapping("/api-keys")
    @Operation(summary = "전체 API Key 목록 조회")
    fun listAllApiKeys(): ResponseEntity<ApiResponse<Any>> {
        val keys = apiKeyRepository.findAll().filter { it.deletedAt == null }
            .map { ApiKeyResponse.from(it) }
        return ResponseEntity.ok(ApiResponse.success(data = keys))
    }

    // === Token Pricing Policy Management ===

    @GetMapping("/token-pricing-policies")
    @Operation(summary = "토큰 가격 정책 목록 조회")
    fun listPricingPolicies(): ResponseEntity<ApiResponse<List<TokenPricingPolicy>>> {
        val policies = tokenPricingPolicyRepository.findAll()
        return ResponseEntity.ok(ApiResponse.success(data = policies))
    }

    @PostMapping("/token-pricing-policies")
    @Operation(summary = "토큰 가격 정책 생성")
    fun createPricingPolicy(@RequestBody request: CreatePricingPolicyRequest): ResponseEntity<ApiResponse<TokenPricingPolicy>> {
        val policy = if (request.clientId != null) {
            TokenPricingPolicy.createForClient(
                clientId = request.clientId,
                vendor = request.vendor,
                model = request.model,
                inputTokenPricePerMillion = request.inputPricePerMillion,
                outputTokenPricePerMillion = request.outputPricePerMillion,
            )
        } else {
            TokenPricingPolicy.create(
                vendor = request.vendor,
                model = request.model,
                inputTokenPricePerMillion = request.inputPricePerMillion,
                outputTokenPricePerMillion = request.outputPricePerMillion,
            )
        }
        val saved = tokenPricingPolicyRepository.save(policy)
        return ResponseEntity.ok(ApiResponse.success(data = saved, message = "가격 정책이 생성되었습니다."))
    }

    @DeleteMapping("/token-pricing-policies/{id}")
    @Operation(summary = "토큰 가격 정책 삭제")
    fun deletePricingPolicy(@PathVariable id: Long): ResponseEntity<ApiResponse<Unit>> {
        tokenPricingPolicyRepository.deleteById(id)
        return ResponseEntity.ok(ApiResponse.success(message = "가격 정책이 삭제되었습니다."))
    }

    // === Quota Management ===

    @GetMapping("/clients/{clientId}/pricing-quotas")
    @Operation(summary = "클라이언트 가격 쿼터 조회")
    fun listPricingQuotas(@PathVariable clientId: Long): ResponseEntity<ApiResponse<Any>> {
        val quotas = clientPricingQuotaRepository.findByClientIdWithLock(clientId)
        return ResponseEntity.ok(ApiResponse.success(data = quotas))
    }

    @GetMapping("/clients/{clientId}/token-quotas")
    @Operation(summary = "클라이언트 토큰 쿼터 조회")
    fun listTokenQuotas(@PathVariable clientId: Long): ResponseEntity<ApiResponse<Any>> {
        val quotas = clientTokenQuotaRepository.findByClientId(clientId)
        return ResponseEntity.ok(ApiResponse.success(data = quotas))
    }

    // === Rate Limit Policy ===

    @GetMapping("/rate-limit-policies")
    @Operation(summary = "Rate Limit 정책 목록 조회")
    fun listRateLimitPolicies(): ResponseEntity<ApiResponse<List<RateLimitPolicyResponse>>> {
        val policies = rateLimitPolicyService.findAll().map { RateLimitPolicyResponse.from(it) }
        return ResponseEntity.ok(ApiResponse.success(data = policies))
    }

    @PostMapping("/rate-limit-policies")
    @Operation(summary = "Rate Limit 정책 생성")
    fun createRateLimitPolicy(
        @RequestBody request: CreateRateLimitPolicyRequest,
    ): ResponseEntity<ApiResponse<RateLimitPolicyResponse>> {
        val saved = rateLimitPolicyService.create(
            vendor = request.vendor,
            tier = request.tier,
            applicationId = request.applicationId,
            rpm = request.rpm,
            rpd = request.rpd,
        )
        return ResponseEntity.ok(
            ApiResponse.success(
                data = RateLimitPolicyResponse.from(saved),
                message = "Rate Limit 정책이 생성되었습니다.",
            ),
        )
    }

    @PutMapping("/rate-limit-policies/{id}")
    @Operation(summary = "Rate Limit 정책 수정 (rpm/rpd)")
    fun updateRateLimitPolicy(
        @PathVariable id: Long,
        @RequestBody request: UpdateRateLimitPolicyRequest,
    ): ResponseEntity<ApiResponse<RateLimitPolicyResponse>> {
        val saved = rateLimitPolicyService.update(id, request.rpm, request.rpd)
        return ResponseEntity.ok(
            ApiResponse.success(
                data = RateLimitPolicyResponse.from(saved),
                message = "Rate Limit 정책이 수정되었습니다.",
            ),
        )
    }

    @DeleteMapping("/rate-limit-policies/{id}")
    @Operation(summary = "Rate Limit 정책 삭제 (soft delete)")
    fun deleteRateLimitPolicy(@PathVariable id: Long): ResponseEntity<ApiResponse<Unit>> {
        rateLimitPolicyService.softDelete(id)
        return ResponseEntity.ok(ApiResponse.success(message = "Rate Limit 정책이 삭제되었습니다."))
    }
}

data class RateLimitPolicyResponse(
    val id: Long,
    val vendor: Vendor,
    val tier: ApiKeyTier,
    val applicationId: String?,
    val rpm: Int,
    val rpd: Int,
) {
    companion object {
        fun from(e: com.example.demo.model.ratelimit.RateLimitPolicyEntity) =
            RateLimitPolicyResponse(
                id = e.id,
                vendor = e.vendor,
                tier = e.tier,
                applicationId = e.applicationId,
                rpm = e.rpm,
                rpd = e.rpd,
            )
    }
}

data class CreateRateLimitPolicyRequest(
    val vendor: Vendor,
    val tier: ApiKeyTier,
    val applicationId: String? = null,
    val rpm: Int,
    val rpd: Int,
)

data class UpdateRateLimitPolicyRequest(
    val rpm: Int,
    val rpd: Int,
)

data class CreateClientRequest(
    val name: String,
    val password: String,
    val description: String? = null,
)

data class IssueAppKeyRequest(
    val name: String,
    val description: String? = null,
)

data class CreatePricingPolicyRequest(
    val vendor: Vendor,
    val model: String,
    val inputPricePerMillion: BigDecimal,
    val outputPricePerMillion: BigDecimal,
    val clientId: Long? = null,
)
