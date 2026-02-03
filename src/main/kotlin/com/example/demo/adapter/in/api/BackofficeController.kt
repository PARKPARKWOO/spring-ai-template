package com.example.demo.adapter.`in`.api

import com.example.demo.adapter.out.persistence.ApiKeyRepository
import com.example.demo.dto.ApiResponse
import com.example.demo.model.Vendor
import com.example.demo.model.api.AnthropicApiKey
import com.example.demo.model.api.ApiKey
import com.example.demo.model.api.GoogleApiKey
import com.example.demo.model.api.OpenAiApiKey
import com.example.demo.model.api.XAiApiKey
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDateTime

/**
 * 백오피스: Application별 AI API 키만 관리 (인증 없음, 내부 관리용).
 */
@RestController
@RequestMapping("/api/backoffice")
@Tag(name = "Backoffice", description = "Application별 API 키 관리")
@io.swagger.v3.oas.annotations.Hidden
class BackofficeController(
    private val apiKeyRepository: ApiKeyRepository,
) {

    @GetMapping("/applications/{applicationId}/api-keys")
    @Operation(summary = "Application별 ApiKey 목록 조회")
    fun getApiKeysByApplication(@PathVariable applicationId: String): ApiResponse<List<ApiKeyInfo>> {
        val apiKeys = apiKeyRepository.findByApplicationIdAndDeletedAtIsNull(applicationId)
        return ApiResponse.success(
            data = apiKeys.map { ApiKeyInfo.fromEntity(it) },
            message = "Application API 키 목록을 조회했습니다."
        )
    }

    @PostMapping("/applications/{applicationId}/api-keys/openai")
    @Operation(summary = "OpenAI ApiKey 등록")
    @Transactional
    fun createOpenAiApiKey(
        @PathVariable applicationId: String,
        @RequestBody request: CreateApiKeyRequest,
    ): ApiResponse<ApiKeyInfo> {
        val now = LocalDateTime.now()
        val apiKey = OpenAiApiKey(
            id = 0L,
            applicationId = applicationId,
            vendor = Vendor.OPENAI,
            description = request.description,
            apiKey = request.apiKey,
            createdAt = now,
            updatedAt = now,
            deletedAt = null
        )
        val saved = apiKeyRepository.save(apiKey)
        return ApiResponse.success(data = ApiKeyInfo.fromEntity(saved), message = "OpenAI ApiKey가 등록되었습니다.")
    }

    @PostMapping("/applications/{applicationId}/api-keys/anthropic")
    @Operation(summary = "Anthropic ApiKey 등록")
    @Transactional
    fun createAnthropicApiKey(
        @PathVariable applicationId: String,
        @RequestBody request: CreateApiKeyRequest,
    ): ApiResponse<ApiKeyInfo> {
        val now = LocalDateTime.now()
        val apiKey = AnthropicApiKey(
            id = 0L,
            applicationId = applicationId,
            vendor = Vendor.ANTHROPIC,
            description = request.description,
            apiKey = request.apiKey,
            createdAt = now,
            updatedAt = now,
            deletedAt = null
        )
        val saved = apiKeyRepository.save(apiKey)
        return ApiResponse.success(data = ApiKeyInfo.fromEntity(saved), message = "Anthropic ApiKey가 등록되었습니다.")
    }

    @PostMapping("/applications/{applicationId}/api-keys/google")
    @Operation(summary = "Google ApiKey 등록")
    @Transactional
    fun createGoogleApiKey(
        @PathVariable applicationId: String,
        @RequestBody request: CreateGoogleApiKeyRequest,
    ): ApiResponse<ApiKeyInfo> {
        val now = LocalDateTime.now()
        val apiKey = GoogleApiKey(
            id = 0L,
            applicationId = applicationId,
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
        return ApiResponse.success(data = ApiKeyInfo.fromEntity(saved), message = "Google ApiKey가 등록되었습니다.")
    }

    @PostMapping("/applications/{applicationId}/api-keys/xai")
    @Operation(summary = "xAI ApiKey 등록")
    @Transactional
    fun createXAiApiKey(
        @PathVariable applicationId: String,
        @RequestBody request: CreateApiKeyRequest,
    ): ApiResponse<ApiKeyInfo> {
        val now = LocalDateTime.now()
        val apiKey = XAiApiKey(
            id = 0L,
            applicationId = applicationId,
            vendor = Vendor.X_AI,
            description = request.description,
            apiKey = request.apiKey,
            createdAt = now,
            updatedAt = now,
            deletedAt = null
        )
        val saved = apiKeyRepository.save(apiKey)
        return ApiResponse.success(data = ApiKeyInfo.fromEntity(saved), message = "xAI ApiKey가 등록되었습니다.")
    }

    @PutMapping("/api-keys/{apiKeyId}")
    @Operation(summary = "ApiKey 수정")
    @Transactional
    fun updateApiKey(
        @PathVariable apiKeyId: Long,
        @RequestBody request: UpdateApiKeyRequest,
    ): ApiResponse<ApiKeyInfo> {
        val apiKey = apiKeyRepository.findById(apiKeyId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "ApiKey를 찾을 수 없습니다.") }
        if (apiKey.deletedAt != null) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "삭제된 ApiKey입니다.")
        }
        val updated = when (apiKey.vendor) {
            Vendor.OPENAI -> {
                val k = apiKey as OpenAiApiKey
                OpenAiApiKey(k.id, k.applicationId, k.vendor, request.description ?: k.description, k.apiKey, k.createdAt, LocalDateTime.now(), k.deletedAt)
            }
            Vendor.ANTHROPIC -> {
                val k = apiKey as AnthropicApiKey
                AnthropicApiKey(k.id, k.applicationId, k.vendor, request.description ?: k.description, k.apiKey, k.createdAt, LocalDateTime.now(), k.deletedAt)
            }
            Vendor.GOOGLE -> {
                val k = apiKey as GoogleApiKey
                GoogleApiKey(k.id, k.applicationId, k.vendor, request.description ?: k.description, k.apiKey, request.projectId ?: k.projectId, request.location ?: k.location, k.createdAt, LocalDateTime.now(), k.deletedAt)
            }
            Vendor.X_AI -> {
                val k = apiKey as XAiApiKey
                XAiApiKey(k.id, k.applicationId, k.vendor, request.description ?: k.description, k.apiKey, k.createdAt, LocalDateTime.now(), k.deletedAt)
            }
        }
        val saved = apiKeyRepository.save(updated)
        return ApiResponse.success(data = ApiKeyInfo.fromEntity(saved), message = "ApiKey가 수정되었습니다.")
    }

    @DeleteMapping("/api-keys/{apiKeyId}")
    @Operation(summary = "ApiKey 삭제 (소프트 삭제)")
    @Transactional
    fun deleteApiKey(@PathVariable apiKeyId: Long): ApiResponse<Unit> {
        val apiKey = apiKeyRepository.findById(apiKeyId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "ApiKey를 찾을 수 없습니다.") }
        if (apiKey.deletedAt != null) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 삭제된 ApiKey입니다.")
        }
        val now = LocalDateTime.now()
        val deleted = when (apiKey.vendor) {
            Vendor.OPENAI -> (apiKey as OpenAiApiKey).let { k -> OpenAiApiKey(k.id, k.applicationId, k.vendor, k.description, k.apiKey, k.createdAt, now, now) }
            Vendor.ANTHROPIC -> (apiKey as AnthropicApiKey).let { k -> AnthropicApiKey(k.id, k.applicationId, k.vendor, k.description, k.apiKey, k.createdAt, now, now) }
            Vendor.GOOGLE -> (apiKey as GoogleApiKey).let { k -> GoogleApiKey(k.id, k.applicationId, k.vendor, k.description, k.apiKey, k.projectId, k.location, k.createdAt, now, now) }
            Vendor.X_AI -> (apiKey as XAiApiKey).let { k -> XAiApiKey(k.id, k.applicationId, k.vendor, k.description, k.apiKey, k.createdAt, now, now) }
        }
        apiKeyRepository.save(deleted)
        return ApiResponse.success(message = "ApiKey가 삭제되었습니다.")
    }

    data class ApiKeyInfo(
        val id: Long,
        val applicationId: String,
        val vendor: String,
        val description: String,
        val apiKey: String,
        val projectId: String? = null,
        val location: String? = null,
        val createdAt: String,
        val updatedAt: String,
    ) {
        companion object {
            fun fromEntity(apiKey: ApiKey): ApiKeyInfo = when (apiKey) {
                is OpenAiApiKey -> ApiKeyInfo(apiKey.id, apiKey.applicationId, apiKey.vendor.name, apiKey.description, apiKey.apiKey, null, null, apiKey.createdAt.toString(), apiKey.updatedAt.toString())
                is AnthropicApiKey -> ApiKeyInfo(apiKey.id, apiKey.applicationId, apiKey.vendor.name, apiKey.description, apiKey.apiKey, null, null, apiKey.createdAt.toString(), apiKey.updatedAt.toString())
                is GoogleApiKey -> ApiKeyInfo(apiKey.id, apiKey.applicationId, apiKey.vendor.name, apiKey.description, apiKey.apiKey, apiKey.projectId, apiKey.location, apiKey.createdAt.toString(), apiKey.updatedAt.toString())
                is XAiApiKey -> ApiKeyInfo(apiKey.id, apiKey.applicationId, apiKey.vendor.name, apiKey.description, apiKey.apiKey, null, null, apiKey.createdAt.toString(), apiKey.updatedAt.toString())
                else -> throw IllegalArgumentException("Unknown ApiKey type")
            }
        }
    }

    data class CreateApiKeyRequest(val apiKey: String, val description: String)
    data class CreateGoogleApiKeyRequest(val apiKey: String, val description: String, val projectId: String? = null, val location: String? = null)
    data class UpdateApiKeyRequest(val apiKey: String? = null, val description: String? = null, val projectId: String? = null, val location: String? = null)
}
