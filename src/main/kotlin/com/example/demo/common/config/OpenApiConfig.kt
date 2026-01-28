package com.example.demo.common.config

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.info.Contact
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.info.License
import io.swagger.v3.oas.annotations.servers.Server
import io.swagger.v3.oas.annotations.security.SecurityScheme
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@OpenAPIDefinition(
    info = Info(
        title = "AI Service API",
        version = "1.0.0",
        description = "AI 서비스 API - 다양한 AI 모델(OpenAI, Anthropic, Google, xAI)을 통합하여 제공하는 RESTful API",
        contact = Contact(
            name = "API Support",
            email = "support@example.com"
        ),
        license = License(
            name = "Apache 2.0",
            url = "https://www.apache.org/licenses/LICENSE-2.0.html"
        )
    ),
    servers = [
        Server(
            url = "/",
            description = "현재 서버 (자동 감지)"
        ),
        Server(
            url = "https://devai.hectodata.co.kr",
            description = "Dev Api Server"
        )
    ]
)
@SecurityScheme(
    name = "sessionAuth",
    type = SecuritySchemeType.APIKEY,
    `in` = SecuritySchemeIn.COOKIE,
    paramName = "JSESSIONID",
    description = "세션 기반 인증 (로그인 후 세션 쿠키 사용)"
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    description = "JWT 토큰 기반 인증 - Authorization 헤더에 'Bearer {JWT토큰}' 형식으로 전송 (예: Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...)"
)
@SecurityScheme(
    name = "appKeyAuth",
    type = SecuritySchemeType.APIKEY,
    `in` = SecuritySchemeIn.HEADER,
    paramName = "X-API-Key",
    description = "AppKey 기반 인증 - X-API-Key 헤더에 AppKey 값을 전송 (예: X-API-Key: appkey_xxxxxxxxxxxxx)"
)
class OpenApiConfig {
    /**
     * Public API 그룹 (Backoffice 제외)
     * /api/ai, /api/client, /api/ai/embedding 등 공개 API만 포함
     */
    @Bean
    fun publicApi(): GroupedOpenApi {
        return GroupedOpenApi.builder()
            .group("public-api")
            .pathsToMatch("/api/ai/**", "/api/client/**", "/api/ai/embedding")
            .pathsToExclude("/api/backoffice/**")
            .build()
    }
    
    /**
     * OpenAPI 서버 URL을 동적으로 설정
     * 현재 요청의 호스트를 자동으로 사용하도록 함
     */
    @Bean
    fun openApiCustomizer(): OpenApiCustomizer {
        return OpenApiCustomizer { openApi ->
            // 서버 목록이 이미 @OpenAPIDefinition에 정의되어 있으므로
            // 여기서는 추가 커스터마이징이 필요하면 수행
            // 기본적으로 "/"로 설정된 서버가 현재 요청의 호스트를 자동으로 사용함
        }
    }
}
