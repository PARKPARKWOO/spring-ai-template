package com.example.demo.dto

import com.example.demo.model.Vendor
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty

@Schema(description = "AI API 요청")
data class AiApiRequest(
    @field:Schema(description = "Application 식별자 (호출 서비스에서 전달, API 키 조회용)", example = "app-uuid-1")
    val applicationId: String? = null,
    @field:Schema(description = "사용할 모델 정의", required = true)
    val models: List<ModelSpec>,
    @field:Schema(description = "대화 내역 메시지 목록. userMessage, SystemMessage, AssistantMessage를 포함할 수 있습니다.", example = "[{\"role\":\"system\",\"content\":\"당신은 도움이 되는 AI 어시스턴트입니다.\"},{\"role\":\"user\",\"content\":\"안녕하세요\"}]")
    @field:NotEmpty
    val messages: List<ChatMessage> = emptyList(),
//    @field:Schema(description = "사용자 프롬프트 (messages가 없을 때 사용)", example = "안녕하세요")
//    val userPrompt: String? = null,
//    @field:Schema(description = "시스템 프롬프트 (선택사항, messages가 없을 때 사용)", example = "당신은 도움이 되는 AI 어시스턴트입니다.")
//    val systemPrompt: String? = null,
    @field:Schema(description = "실제 사용하는 사용자의 Id 혹은 테넌트 Id", example = "1", required = true, defaultValue = "1")
    val sessionId: String,
    @field:Schema(description = "Structured Output을 위한 JSON Schema (선택사항). JSON 객체 또는 JSON 문자열로 제공 가능합니다.", example = "{\"type\":\"object\",\"properties\":{\"answer\":{\"type\":\"string\"}},\"required\":[\"answer\"]}")
    @field:JsonDeserialize(using = JsonSchemaDeserializer::class)
    val responseSchema: String? = null,
    @field:Schema(description = "요청 타임아웃 (초 단위). AI 모델 호출 시 최대 대기 시간입니다. 기본값은 120초입니다.", example = "120", defaultValue = "120")
    val timeoutSeconds: Int? = null,
)

@Schema(description = "모델 스펙")
@JsonDeserialize(using = ModelSpecDeserializer::class)
data class ModelSpec(
    @field:Schema(description = "사용할 AI 벤더", example = "OPENAI", required = true)
    val vendor: Vendor,
    @field:Schema(description = "모델 버전", example = "gpt-4o-mini", required = true)
    val version: String,
    @field:Schema(description = "벤더별 옵션 (선택사항). 벤더에 따라 다른 옵션을 설정할 수 있습니다.")
    val vendorOptions: VendorOptions? = null,
)

@Schema(description = "벤더별 옵션 (Chat API용)")
sealed class VendorOptions : VendorOptionsBase {
    @Schema(description = "Gemini 전용 옵션")
    data class GeminiOptions(
        @field:Schema(description = "URL Context 목록. 제공된 URL의 내용을 컨텍스트로 사용합니다.", example = "[\"https://example.com/article\"]")
        val urlContexts: List<String>? = null,
        
        @field:Schema(description = "Google Search Grounding 활성화 여부. true일 경우 Google Search를 사용하여 최신 정보를 검색합니다.", example = "false")
        val enableGoogleSearch: Boolean? = null,
        
        @field:Schema(description = "Tool Calling을 위한 도구 이름 목록", example = "[\"weatherFunction\", \"calculatorFunction\"]")
        val toolNames: List<String>? = null,

        @field:Schema(description = "캐시된 콘텐츠 사용 여부. true일 경우 이전에 캐시된 콘텐츠를 재사용합니다. (비용 절감 및 응답 속도 향상)", example = "false")
        val useCachedContent: Boolean? = null,
        
        @field:Schema(description = "사용할 캐시된 콘텐츠의 이름. useCachedContent가 true일 때 필수입니다.", example = "cachedContent/my-cache-123")
        val cachedContentName: String? = null,
    ) : VendorOptions()
    
    @Schema(description = "OpenAI 전용 옵션")
    data class OpenAIOptions(
        @field:Schema(description = "Tool Calling을 위한 도구 이름 목록", example = "[\"weatherFunction\", \"calculatorFunction\"]")
        val toolNames: List<String>? = null,
    ) : VendorOptions()
    
    @Schema(description = "Anthropic 전용 옵션")
    data class AnthropicOptions(
        @field:Schema(description = "Tool Calling을 위한 도구 이름 목록", example = "[\"weatherFunction\", \"calculatorFunction\"]")
        val toolNames: List<String>? = null,
        
        @field:Schema(description = "캐시 전략. SYSTEM_ONLY(시스템 프롬프트만 캐시), CONVERSATION_HISTORY(대화 기록 캐시), SYSTEM_AND_TOOLS(시스템 프롬프트와 도구 정의 캐시)", example = "SYSTEM_ONLY")
        val cacheStrategy: String? = null,
        
        @field:Schema(description = "캐시 TTL (Time To Live). ONE_HOUR(1시간), FIVE_MINUTES(5분, 기본값)", example = "ONE_HOUR")
        val cacheTtl: String? = null,
    ) : VendorOptions()
    
    @Schema(description = "Grok (xAI) 전용 옵션")
    data class GrokOptions(
        @field:Schema(description = "Tool Calling을 위한 도구 이름 목록", example = "[\"weatherFunction\", \"calculatorFunction\"]")
        val toolNames: List<String>? = null,
    ) : VendorOptions()
}
