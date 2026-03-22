package com.example.demo.dto

/**
 * AI 호출에 필요한 모든 컨텍스트를 담는 통합 요청 객체.
 *
 * 기존의 10개 이상 파라미터를 하나의 컨텍스트로 통합하여
 * 인터페이스를 깔끔하게 유지하고 새로운 옵션 추가 시 시그니처 변경 없이 확장 가능.
 */
data class AiCallContext(
    val messages: List<ChatMessage>,
    val applicationId: String,
    val sessionId: String,
    val maxTokens: Int? = null,
    val jsonSchema: String? = null,
    val model: String? = null,
    val vendorOptions: VendorOptions? = null,
    val requestType: String? = null,
    val timeoutSeconds: Int? = null,
) {
    companion object {
        const val DEFAULT_MAX_TOKENS = 2000
        const val DEFAULT_TIMEOUT_SECONDS = 120

        fun from(
            request: AiApiRequest,
            applicationId: String,
            modelSpec: ModelSpec,
            responseSchema: String? = null,
        ): AiCallContext = AiCallContext(
            messages = request.messages,
            applicationId = applicationId,
            sessionId = request.sessionId,
            maxTokens = request.maxTokens,
            jsonSchema = resolveJsonSchema(modelSpec, responseSchema),
            model = modelSpec.version,
            vendorOptions = modelSpec.vendorOptions,
            requestType = request.requestType,
            timeoutSeconds = request.timeoutSeconds,
        )

        /**
         * 벤더별 JSON Schema 지원 여부에 따라 스키마를 결정.
         * Anthropic도 Claude 3.5+ 이후 Structured Output을 지원하므로 스키마 전달.
         */
        private fun resolveJsonSchema(modelSpec: ModelSpec, commonResponseSchema: String?): String? =
            commonResponseSchema
    }

    fun effectiveMaxTokens(): Int = maxTokens ?: DEFAULT_MAX_TOKENS

    fun effectiveTimeoutMs(): Long = ((timeoutSeconds ?: DEFAULT_TIMEOUT_SECONDS) * 1000).toLong()
}
