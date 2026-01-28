package com.example.demo.adapter.`in`.api

import com.example.demo.adapter.out.persistence.ClientRepository
import com.example.demo.business.AppKeyService
import com.example.demo.business.JwtService
import com.example.demo.dto.ApiResponse
import com.example.demo.model.AppKey
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import com.example.demo.common.annotation.CurrentClientId
import io.swagger.v3.oas.annotations.Parameter
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

@Schema(description = "로그인 요청")
data class LoginRequest(
    @field:Schema(
        description = "클라이언트 이름", 
        example = "HectoFinancial", 
        required = true
    )
    val name: String,

    @field:Schema(
        description = "평문 비밀번호를 입력하세요 (DB에 저장된 BCrypt 해시값이 아닙니다). " +
                "테스트 계정: HectoFinancial / password", 
        example = "password", 
        required = true
    )
    val password: String,
)

@Schema(description = "로그인 응답")
data class LoginResponse(
    @field:Schema(description = "클라이언트 ID", example = "1")
    val clientId: Long,

    @field:Schema(description = "클라이언트 이름", example = "HectoData")
    val clientName: String,

    @field:Schema(description = "JWT 액세스 토큰", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    val accessToken: String? = null,

    @field:Schema(description = "JWT 리프레시 토큰", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    val refreshToken: String? = null,

    @field:Schema(description = "응답 메시지", example = "Login successful")
    val message: String,
)

@Schema(description = "JWT 토큰 갱신 요청")
data class RefreshTokenRequest(
    @field:Schema(description = "리프레시 토큰", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...", required = true)
    val refreshToken: String,
)

@Schema(description = "JWT 토큰 갱신 응답")
data class RefreshTokenResponse(
    @field:Schema(description = "새로운 JWT 액세스 토큰", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    val accessToken: String,

    @field:Schema(description = "새로운 JWT 리프레시 토큰", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    val refreshToken: String? = null,
)

@Schema(description = "AppKey 발급 요청")
data class IssueAppKeyRequest(
    @field:Schema(description = "AppKey 이름", example = "Production API Key", required = true)
    val name: String,

    @field:Schema(description = "AppKey 설명", example = "프로덕션 환경용 API 키")
    val description: String? = null,
)

@Schema(description = "AppKey 발급 응답")
data class IssueAppKeyResponse(
    @field:Schema(description = "발급된 AppKey (이 값은 한 번만 표시되므로 안전하게 보관하세요)", example = "appkey_xxxxxxxxxxxxx")
    val appKey: String,

    @field:Schema(description = "AppKey ID", example = "1")
    val appKeyId: Long,

    @field:Schema(description = "AppKey 이름", example = "Production API Key")
    val name: String,

    @field:Schema(description = "만료일 (null이면 무제한)", example = "2125-01-01T00:00:00")
    val expiresAt: String?,
)

@Schema(description = "AppKey 정보 (키 값은 포함되지 않음)")
data class AppKeyInfo(
    @field:Schema(description = "AppKey ID", example = "1")
    val id: Long,

    @field:Schema(description = "AppKey 이름", example = "Production API Key")
    val name: String,

    @field:Schema(description = "AppKey 설명", example = "프로덕션 환경용 API 키")
    val description: String?,

    @field:Schema(description = "활성화 여부", example = "true")
    val isActive: Boolean,

    @field:Schema(description = "만료일 (null이면 무제한)", example = "2125-01-01T00:00:00")
    val expiresAt: String?,

    @field:Schema(description = "마지막 사용 시간", example = "2025-01-01T12:00:00")
    val lastUsedAt: String?,

    @field:Schema(description = "생성일", example = "2025-01-01T00:00:00")
    val createdAt: String,
)

@RestController
@RequestMapping("/api/client")
@Tag(name = "Client", description = "클라이언트 인증 및 AppKey 관리 API")
class ClientController(
    private val clientRepository: ClientRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val appKeyService: AppKeyService,
) {
    @PostMapping("/token/issue")
    @Operation(
        summary = "로그인",
        description = "클라이언트 이름과 평문 비밀번호로 로그인하여 JWT 토큰을 생성합니다. " +
                "비밀번호는 평문으로 입력하세요 (BCrypt 해시값이 아닙니다). " +
                "테스트 계정: name='HectoFinancial', password='password'"
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(
                responseCode = "200",
                description = "로그인 성공",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(
                        implementation = ApiResponse::class,
                        example = "{\"code\":\"SUCCESS\",\"message\":\"Login successful\",\"data\":{\"clientId\":1,\"clientName\":\"HectoFinancial\",\"accessToken\":\"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...\",\"refreshToken\":\"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...\",\"message\":\"Login successful\"},\"error\":null}"
                    )
                )]
            ),
            SwaggerApiResponse(
                responseCode = "401",
                description = "잘못된 사용자 이름 또는 비밀번호. errorCode: AUTH_INVALID_CREDENTIALS",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ApiResponse::class)
                )]
            )
        ]
    )
    fun login(
        @RequestBody request: LoginRequest,
    ): ApiResponse<LoginResponse> {
        val client = clientRepository.findByName(request.name)
        
        if (client == null || client.deletedAt != null) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password")
        }
        
        // 디버깅: 비밀번호 매칭 확인
        println("로그인 시도 - 클라이언트: ${request.name}, 입력 비밀번호: ${request.password}")
        println("  저장된 비밀번호 해시: ${client.password.take(30)}...")
        val passwordMatches = passwordEncoder.matches(request.password, client.password)
        println("  비밀번호 매칭 결과: $passwordMatches")
        
        if (!passwordMatches) {
            // 디버깅 정보 출력 (프로덕션에서는 제거)
            println("✗ 로그인 실패 - 클라이언트: ${request.name}, 입력 비밀번호: ${request.password}")
            println("  저장된 해시: ${client.password.take(30)}...")
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password")
        }
        
        println("✓ 로그인 성공 - 클라이언트: ${request.name}")
        
        // JWT 토큰 생성 (Stateless: 세션 사용 안 함)
        val accessToken = jwtService.generateToken(client.id, client.name, isRefreshToken = false)
        val refreshToken = jwtService.generateToken(client.id, client.name, isRefreshToken = true)
        
        val loginResponse = LoginResponse(
            clientId = client.id,
            clientName = client.name,
            accessToken = accessToken,
            refreshToken = refreshToken,
            message = "Login successful"
        )
        
        return ApiResponse.success(
            data = loginResponse,
            message = "로그인에 성공했습니다."
        )
    }

//    @PostMapping("/logout")
//    @Operation(
//        summary = "로그아웃",
//        description = "현재 세션을 무효화하여 로그아웃합니다. (JWT 토큰은 클라이언트에서 삭제해야 합니다.)"
//    )
//    @ApiResponses(
//        value = [
//            SwaggerApiResponse(
//                responseCode = "200",
//                description = "로그아웃 성공",
//                content = [Content(
//                    mediaType = "application/json",
//                    schema = Schema(implementation = ApiResponse::class)
//                )]
//            )
//        ]
//    )
//    fun logout(session: HttpSession): ApiResponse<Unit> {
//        session.invalidate()
//        return ApiResponse.success(
//            message = "로그아웃되었습니다."
//        )
//    }
    
    @PostMapping("/refresh")
    @Operation(
        summary = "JWT 토큰 갱신",
        description = "리프레시 토큰을 사용하여 새로운 액세스 토큰을 발급받습니다."
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(
                responseCode = "200",
                description = "토큰 갱신 성공",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(
                        implementation = ApiResponse::class,
                        example = "{\"code\":\"SUCCESS\",\"message\":\"토큰이 성공적으로 갱신되었습니다.\",\"data\":{\"accessToken\":\"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...\",\"refreshToken\":\"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...\"},\"error\":null}"
                    )
                )]
            ),
            SwaggerApiResponse(
                responseCode = "401",
                description = "유효하지 않은 리프레시 토큰. 가능한 errorCode: CLIENT_REFRESH_TOKEN_INVALID, AUTH_TOKEN_EXPIRED",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ApiResponse::class)
                )]
            )
        ]
    )
    fun refreshToken(
        @RequestBody request: RefreshTokenRequest,
    ): ApiResponse<RefreshTokenResponse> {
        val refreshToken = request.refreshToken
        
        // 리프레시 토큰 유효성 검증
        if (!jwtService.validateToken(refreshToken) || !jwtService.isRefreshToken(refreshToken)) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token")
        }
        
        val clientId = jwtService.getClientIdFromToken(refreshToken)
        val clientName = jwtService.getClientNameFromToken(refreshToken)
        
        if (clientId == null || clientName == null) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token")
        }
        
        // 새로운 액세스 토큰 생성
        val newAccessToken = jwtService.generateToken(clientId, clientName, isRefreshToken = false)
        
        // 선택적으로 새로운 리프레시 토큰도 생성 (토큰 로테이션)
        val newRefreshToken = jwtService.generateToken(clientId, clientName, isRefreshToken = true)
        
        val response = RefreshTokenResponse(
            accessToken = newAccessToken,
            refreshToken = newRefreshToken
        )
        
        return ApiResponse.success(
            data = response,
            message = "토큰이 성공적으로 갱신되었습니다."
        )
    }

    // ============================================
    // AppKey 관리 API
    // ============================================

    @PostMapping("/app-keys")
    @Operation(
        summary = "AppKey 발급",
        description = "새로운 AppKey를 발급합니다. 발급된 키는 한 번만 표시되므로 안전하게 보관하세요. " +
                "인증 방법: Authorization 헤더에 'Bearer {JWT토큰}' 또는 X-API-Key 헤더에 AppKey를 포함하세요."
    )
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "appKeyAuth")
    @ApiResponses(
        value = [
            SwaggerApiResponse(
                responseCode = "200",
                description = "AppKey 발급 성공",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(
                        implementation = ApiResponse::class,
                        example = "{\"code\":\"SUCCESS\",\"message\":\"AppKey가 성공적으로 발급되었습니다.\",\"data\":{\"appKey\":\"appkey_xxxxxxxxxxxxx\",\"id\":1,\"name\":\"Production API Key\",\"description\":\"프로덕션 환경용 API 키\",\"isActive\":true,\"expiresAt\":\"2125-01-01T00:00:00\",\"lastUsedAt\":null,\"createdAt\":\"2025-01-01T00:00:00\"},\"error\":null}"
                    )
                )]
            ),
            SwaggerApiResponse(
                responseCode = "400",
                description = "잘못된 요청입니다. (예: AppKey 이름이 비어있음)",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ApiResponse::class)
                )]
            ),
            SwaggerApiResponse(
                responseCode = "401",
                description = "인증되지 않은 사용자입니다. 가능한 errorCode: AUTH_TOKEN_MISSING, AUTH_APP_KEY_INVALID",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ApiResponse::class)
                )]
            )
        ]
    )
    fun issueAppKey(
        @RequestBody request: IssueAppKeyRequest,
        @Parameter(hidden = true)
        @CurrentClientId clientId: Long,
    ): ApiResponse<IssueAppKeyResponse> {

        val (originalKey, appKey) = appKeyService.issueAppKey(
            clientId = clientId,
            name = request.name,
            description = request.description
        )

        val response = IssueAppKeyResponse(
            appKey = originalKey,
            appKeyId = appKey.id,
            name = appKey.name,
            expiresAt = appKey.expiresAt?.toString()
        )

        return ApiResponse.success(
            data = response,
            message = "AppKey가 성공적으로 발급되었습니다. 이 키는 한 번만 표시되므로 안전하게 보관하세요."
        )
    }

    @GetMapping("/app-keys")
    @Operation(
        summary = "AppKey 목록 조회",
        description = "현재 클라이언트의 모든 AppKey 목록을 조회합니다. (키 값은 포함되지 않음) " +
                "인증 방법: Authorization 헤더에 'Bearer {JWT토큰}' 또는 X-API-Key 헤더에 AppKey를 포함하세요."
    )
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "appKeyAuth")
    @ApiResponses(
        value = [
            SwaggerApiResponse(
                responseCode = "200",
                description = "조회 성공",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(
                        implementation = ApiResponse::class,
                        example = "{\"code\":\"SUCCESS\",\"message\":\"AppKey 목록을 성공적으로 조회했습니다.\",\"data\":[{\"id\":1,\"name\":\"Production API Key\",\"description\":\"프로덕션 환경용 API 키\",\"isActive\":true,\"expiresAt\":\"2125-01-01T00:00:00\",\"lastUsedAt\":\"2025-01-01T12:00:00\",\"createdAt\":\"2025-01-01T00:00:00\"}],\"error\":null}"
                    )
                )]
            ),
            SwaggerApiResponse(
                responseCode = "401",
                description = "인증되지 않은 사용자입니다. 가능한 errorCode: AUTH_TOKEN_MISSING, AUTH_APP_KEY_INVALID",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ApiResponse::class)
                )]
            )
        ]
    )
    fun getAppKeys(
        @Parameter(hidden = true)
        @CurrentClientId clientId: Long,
    ): ApiResponse<List<AppKeyInfo>> {

        val appKeys = appKeyService.getAppKeysByClientId(clientId)
        val appKeyInfos = appKeys.map { appKey ->
            AppKeyInfo(
                id = appKey.id,
                name = appKey.name,
                description = appKey.description,
                isActive = appKey.isActive,
                expiresAt = appKey.expiresAt?.toString(),
                lastUsedAt = appKey.lastUsedAt?.toString(),
                createdAt = appKey.createdAt.toString()
            )
        }

        return ApiResponse.success(
            data = appKeyInfos,
            message = "AppKey 목록을 성공적으로 조회했습니다."
        )
    }

    @DeleteMapping("/app-keys/{appKeyId}")
    @Operation(
        summary = "AppKey 삭제",
        description = "AppKey를 삭제합니다. (소프트 삭제) " +
                "인증 방법: Authorization 헤더에 'Bearer {JWT토큰}' 또는 X-API-Key 헤더에 AppKey를 포함하세요."
    )
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "appKeyAuth")
    @ApiResponses(
        value = [
            SwaggerApiResponse(
                responseCode = "200",
                description = "삭제 성공",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(
                        implementation = ApiResponse::class,
                        example = "{\"code\":\"SUCCESS\",\"message\":\"AppKey가 성공적으로 삭제되었습니다.\",\"data\":null,\"error\":null}"
                    )
                )]
            ),
            SwaggerApiResponse(
                responseCode = "401",
                description = "인증되지 않은 사용자입니다. 가능한 errorCode: AUTH_TOKEN_MISSING, AUTH_APP_KEY_INVALID",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ApiResponse::class)
                )]
            ),
            SwaggerApiResponse(
                responseCode = "403",
                description = "다른 클라이언트의 AppKey는 삭제할 수 없습니다. errorCode: CLIENT_APP_KEY_OWNERSHIP_DENIED",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ApiResponse::class)
                )]
            ),
            SwaggerApiResponse(
                responseCode = "404",
                description = "AppKey를 찾을 수 없습니다. errorCode: CLIENT_APP_KEY_NOT_FOUND",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ApiResponse::class)
                )]
            )
        ]
    )
    fun deleteAppKey(
        @PathVariable appKeyId: Long,
        @Parameter(hidden = true)
        @CurrentClientId clientId: Long,
    ): ApiResponse<Unit> {

        val deleted = appKeyService.deleteAppKey(appKeyId, clientId)
        if (!deleted) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "AppKey not found")
        }

        return ApiResponse.success(
            message = "AppKey가 성공적으로 삭제되었습니다."
        )
    }

    @PostMapping("/app-keys/{appKeyId}/deactivate")
    @Operation(
        summary = "AppKey 비활성화",
        description = "AppKey를 비활성화합니다. 비활성화된 키는 사용할 수 없습니다. " +
                "인증 방법: Authorization 헤더에 'Bearer {JWT토큰}' 또는 X-API-Key 헤더에 AppKey를 포함하세요."
    )
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "appKeyAuth")
    @ApiResponses(
        value = [
            SwaggerApiResponse(
                responseCode = "200",
                description = "비활성화 성공",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(
                        implementation = ApiResponse::class,
                        example = "{\"code\":\"SUCCESS\",\"message\":\"AppKey가 성공적으로 비활성화되었습니다.\",\"data\":null,\"error\":null}"
                    )
                )]
            ),
            SwaggerApiResponse(
                responseCode = "401",
                description = "인증되지 않은 사용자입니다. 가능한 errorCode: AUTH_TOKEN_MISSING, AUTH_APP_KEY_INVALID",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ApiResponse::class)
                )]
            ),
            SwaggerApiResponse(
                responseCode = "403",
                description = "다른 클라이언트의 AppKey는 비활성화할 수 없습니다. errorCode: CLIENT_APP_KEY_OWNERSHIP_DENIED",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ApiResponse::class)
                )]
            ),
            SwaggerApiResponse(
                responseCode = "404",
                description = "AppKey를 찾을 수 없습니다. errorCode: CLIENT_APP_KEY_NOT_FOUND",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ApiResponse::class)
                )]
            )
        ]
    )
    fun deactivateAppKey(
        @PathVariable appKeyId: Long,
        @Parameter(hidden = true)
        @CurrentClientId clientId: Long,
    ): ApiResponse<Unit> {

        val deactivated = appKeyService.deactivateAppKey(appKeyId, clientId)
        if (!deactivated) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "AppKey not found")
        }

        return ApiResponse.success(
            message = "AppKey가 성공적으로 비활성화되었습니다."
        )
    }
}

