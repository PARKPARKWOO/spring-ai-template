package com.example.demo.adapter.`in`.api

import com.example.demo.dto.ApiResponse
import com.example.demo.model.ApiKeyTier
import com.example.demo.model.Vendor
import com.example.demo.model.api.ApiKey
import com.example.demo.business.ApiKeyService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/api-keys")
@Tag(name = "API Key", description = "AI 벤더 API Key 관리")
class ApiKeyController(
    private val apiKeyService: ApiKeyService,
) {
    @PostMapping
    @Operation(summary = "API Key 등록", description = "AI 벤더별 API Key를 등록합니다.")
    fun register(
        @RequestBody request: RegisterApiKeyRequest,
    ): ResponseEntity<ApiResponse<ApiKeyResponse>> {
        val apiKey = apiKeyService.register(
            applicationId = request.applicationId,
            vendor = request.vendor,
            tier = request.tier,
            apiKey = request.apiKey,
            description = request.description,
            projectId = request.projectId,
            location = request.location,
        )
        return ResponseEntity.ok(
            ApiResponse.success(
                data = ApiKeyResponse.from(apiKey),
                message = "API Key가 등록되었습니다.",
            ),
        )
    }

    @GetMapping
    @Operation(summary = "API Key 목록 조회", description = "Application별 등록된 API Key 목록을 조회합니다.")
    fun list(
        @RequestParam applicationId: String,
        @RequestParam(required = false) vendor: Vendor?,
        @RequestParam(required = false) tier: ApiKeyTier?,
    ): ResponseEntity<ApiResponse<List<ApiKeyResponse>>> {
        val keys = apiKeyService.list(applicationId, vendor, tier)
        return ResponseEntity.ok(
            ApiResponse.success(
                data = keys.map { ApiKeyResponse.from(it) },
                message = "API Key 목록을 조회했습니다.",
            ),
        )
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "API Key 삭제", description = "API Key를 소프트 삭제합니다.")
    fun delete(
        @PathVariable id: Long,
    ): ResponseEntity<ApiResponse<Unit>> {
        apiKeyService.delete(id)
        return ResponseEntity.ok(
            ApiResponse.success(message = "API Key가 삭제되었습니다."),
        )
    }
}

data class RegisterApiKeyRequest(
    val applicationId: String? = null,
    val vendor: Vendor,
    val tier: ApiKeyTier = ApiKeyTier.FREE,
    val apiKey: String,
    val description: String = "",
    val projectId: String? = null,
    val location: String? = null,
)

data class ApiKeyResponse(
    val id: Long,
    val applicationId: String?,
    val vendor: Vendor,
    val tier: ApiKeyTier,
    val description: String,
    val maskedKey: String,
    val createdAt: String,
) {
    companion object {
        fun from(apiKey: ApiKey): ApiKeyResponse {
            val raw = apiKey.getApiKeyValue()
            val masked = if (raw.length > 8) "${raw.take(4)}****${raw.takeLast(4)}" else "****"
            return ApiKeyResponse(
                id = apiKey.id,
                applicationId = apiKey.applicationId,
                vendor = apiKey.vendor,
                tier = apiKey.tier,
                description = apiKey.description,
                maskedKey = masked,
                createdAt = apiKey.createdAt.toString(),
            )
        }
    }
}
