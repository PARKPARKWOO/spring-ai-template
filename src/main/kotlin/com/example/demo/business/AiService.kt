package com.example.demo.business

import com.example.demo.adapter.out.client.AiApiFactory
import com.example.demo.business.exception.AiServiceException
import org.slf4j.LoggerFactory
import com.example.demo.dto.AiApiRequest
import com.example.demo.dto.AiApiResponse
import com.example.demo.dto.VendorOptions
import com.example.demo.model.ApiErrorCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import java.time.Duration
import java.time.Instant

/**
 * 벤더별 옵션을 담는 데이터 클래스
 */
private data class VendorSpecificOptions(
    val jsonSchema: String?,
    val urlContexts: List<String>?,
    val enableGoogleSearch: Boolean?,
    val toolNames: List<String>?,
    // Gemini 캐시 옵션
    val useCachedContent: Boolean? = null,
    val cachedContentName: String? = null,
    // Anthropic 캐시 옵션
    val cacheStrategy: String? = null,
    val cacheTtl: String? = null,
)

@Service
class AiService(
    private val aiApiFactory: AiApiFactory,
) {
    private val log = LoggerFactory.getLogger(AiService::class.java)
    suspend fun call(
        aiApiRequest: AiApiRequest,
        applicationId: String,
    ): List<AiApiResponse> =
        coroutineScope {
            if (aiApiRequest.messages.isEmpty()) {
                throw AiServiceException(
                    ApiErrorCode.AI_SYSTEM_PROMPT_REQUIRED,
                    "요청에는 messages가 필수입니다.",
                )
            }

            aiApiRequest.models
                .map { modelSpec ->
                    async(Dispatchers.IO) {
                        val start = Instant.now()
                        log.info("api call start={}", start)
                        val client = aiApiFactory.getClient(modelSpec.vendor)
                        val options = extractVendorOptions(modelSpec, aiApiRequest.responseSchema)
                        val timeoutMs = (aiApiRequest.timeoutSeconds ?: 120) * 1000L

                        val response =
                            try {
                                withTimeout(timeoutMs) {
                                    client.call(
                                        aiApiRequest.messages,
                                        applicationId,
                                        aiApiRequest.sessionId,
                                        options.jsonSchema,
                                        options.urlContexts,
                                        options.enableGoogleSearch,
                                        options.toolNames,
                                        modelSpec.version,
                                        options.useCachedContent,
                                        options.cachedContentName,
                                        options.cacheStrategy,
                                        options.cacheTtl,
                                    )
                                }
                            } catch (e: TimeoutCancellationException) {
                                log.warn("AI API call timeout: vendor={}, applicationId={}, timeout={}ms", modelSpec.vendor, applicationId, timeoutMs, e)
                                AiApiResponse.failure(ApiErrorCode.AI_MODEL_TIMEOUT.name, modelSpec.vendor)
                            } catch (e: AiServiceException) {
                                log.error("AI API call failed: vendor={}, applicationId={}, errorCode={}, message={}", modelSpec.vendor, applicationId, e.errorCode.name, e.message, e)
                                AiApiResponse.failure(e.errorCode.name, modelSpec.vendor)
                            } catch (e: Exception) {
                                log.error("Unexpected error during AI API call: vendor={}, applicationId={}", modelSpec.vendor, applicationId, e)
                                AiApiResponse.failure(ApiErrorCode.COMMON_INTERNAL_SERVER_ERROR.name, modelSpec.vendor)
                            }

                        val end = Instant.now()
                        val took = Duration.between(start, end)
                        log.info("api call end={} took={}ms ({}s) vendor={} applicationId={}", end, took.toMillis(), "%.3f".format(took.toMillis() / 1000.0), modelSpec.vendor, applicationId)
                        response
                    }
                }.awaitAll()
        }

    /**
     * 벤더별 옵션 추출
     * ModelSpec의 vendorOptions와 공통 responseSchema를 조합하여 각 벤더에 맞는 옵션 반환
     */
    private fun extractVendorOptions(
        modelSpec: com.example.demo.dto.ModelSpec,
        commonResponseSchema: String?,
    ): VendorSpecificOptions =
        when (val vendorOptions = modelSpec.vendorOptions) {
            is VendorOptions.GeminiOptions ->
                VendorSpecificOptions(
                    jsonSchema = commonResponseSchema,
                    urlContexts = vendorOptions.urlContexts,
                    enableGoogleSearch = vendorOptions.enableGoogleSearch,
                    toolNames = vendorOptions.toolNames,
                    useCachedContent = vendorOptions.useCachedContent,
                    cachedContentName = vendorOptions.cachedContentName,
                )

            is VendorOptions.OpenAIOptions ->
                VendorSpecificOptions(
                    jsonSchema = commonResponseSchema,
                    urlContexts = null,
                    enableGoogleSearch = null,
                    toolNames = vendorOptions.toolNames,
                )

            is VendorOptions.AnthropicOptions ->
                VendorSpecificOptions(
                    jsonSchema = null, // Anthropic은 Structured Output 미지원
                    urlContexts = null,
                    enableGoogleSearch = null,
                    toolNames = vendorOptions.toolNames,
                    cacheStrategy = vendorOptions.cacheStrategy,
                    cacheTtl = vendorOptions.cacheTtl,
                )

            is VendorOptions.GrokOptions ->
                VendorSpecificOptions(
                    jsonSchema = commonResponseSchema,
                    urlContexts = null,
                    enableGoogleSearch = null,
                    toolNames = vendorOptions.toolNames,
                )

            null ->
                VendorSpecificOptions(
                    jsonSchema = commonResponseSchema,
                    urlContexts = null,
                    enableGoogleSearch = null,
                    toolNames = null,
                )
        }

    /**
     * 스트리밍 방식으로 AI 응답을 받습니다.
     * 여러 모델을 동시에 스트리밍하여 Flux로 병합하여 반환합니다.
     */
    suspend fun stream(
        aiApiRequest: AiApiRequest,
        applicationId: String,
    ): Flux<String> {
        val start = Instant.now()
        log.info("api stream call start={}", start)
        if (aiApiRequest.models.isEmpty()) {
            throw AiServiceException(ApiErrorCode.AI_MODELS_EMPTY, "스트리밍 요청에는 최소 하나의 모델이 필요합니다.")
        }
        if (aiApiRequest.messages.isEmpty()) {
            throw AiServiceException(ApiErrorCode.AI_SYSTEM_PROMPT_REQUIRED, "스트리밍 요청에는 messages가 필수입니다.")
        }

        val streams = aiApiRequest.models.map { modelSpec ->
            val client = aiApiFactory.getClient(modelSpec.vendor)
            val options = extractVendorOptions(modelSpec, aiApiRequest.responseSchema)
            val timeoutMs = (aiApiRequest.timeoutSeconds ?: 120) * 1000L
            client
                .stream(
                    aiApiRequest.messages,
                    applicationId,
                    aiApiRequest.sessionId,
                    options.jsonSchema,
                    options.urlContexts,
                    options.enableGoogleSearch,
                    options.toolNames,
                    modelSpec.version,
                    options.useCachedContent,
                    options.cachedContentName,
                    options.cacheStrategy,
                    options.cacheTtl,
                )
                .timeout(java.time.Duration.ofMillis(timeoutMs))
                .map { chunk -> "[${modelSpec.vendor}:${modelSpec.version}] $chunk" }
                .doOnComplete { log.info("Stream completed for vendor: ${modelSpec.vendor}, model: ${modelSpec.version}, applicationId: $applicationId") }
                .doOnError { error -> log.error("Stream error for vendor: ${modelSpec.vendor}, model: ${modelSpec.version}, applicationId: $applicationId", error) }
        }

        return Flux.merge(streams)
            .doOnComplete {
                val took = Duration.between(start, Instant.now())
                log.info("All streams completed. Total time: {}ms ({}s) applicationId={}", took.toMillis(), "%.3f".format(took.toMillis() / 1000.0), applicationId)
            }
            .doOnError { error -> log.error("Merged stream error for applicationId: $applicationId", error) }
    }
}
