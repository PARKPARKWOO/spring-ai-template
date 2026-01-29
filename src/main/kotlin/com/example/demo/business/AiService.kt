package com.example.demo.business

import com.example.demo.adapter.out.client.AiApiFactory
import com.example.demo.business.exception.AiServiceException
import com.example.demo.common.logger
import com.example.demo.dto.AiApiRequest
import com.example.demo.dto.AiApiResponse
import com.example.demo.dto.VendorOptions
import com.example.demo.model.ApiErrorCode
import com.example.demo.model.Vendor
import io.swagger.v3.oas.models.responses.ApiResponse
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
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
    private val aiApiFactory: AiApiFactory
) {
    suspend fun call(
        aiApiRequest: AiApiRequest,
        clientId: Long,
    ): List<AiApiResponse> = coroutineScope {
        if (aiApiRequest.messages.isEmpty()) {
            throw AiServiceException(
                ApiErrorCode.AI_SYSTEM_PROMPT_REQUIRED,
                "요청에는 messages가 필수입니다."
            )
        }

        aiApiRequest.models.map { modelSpec ->
            async(Dispatchers.IO) {
                val start = Instant.now()
                logger().info("api call start={}", start)
                val client = aiApiFactory.getClient(modelSpec.vendor)

                // 벤더별 옵션 추출
                val options = extractVendorOptions(
                    modelSpec,
                    aiApiRequest.responseSchema
                )

                // timeout 설정 (기본값: 120초)
                val timeoutMs = (aiApiRequest.timeoutSeconds ?: 120) * 1000L

                val response = try {
                    withTimeout(timeoutMs) {
                        // messages가 있으면 messages를 사용, 없으면 기존 userPrompt/systemPrompt 사용
                        client.call(
                            aiApiRequest.messages,
                            clientId,
                            aiApiRequest.sessionId,
                            options.jsonSchema,
                            options.urlContexts,
                            options.enableGoogleSearch,
                            options.toolNames,
                            modelSpec.version,
                            options.useCachedContent,
                            options.cachedContentName,
                            options.cacheStrategy,
                            options.cacheTtl
                        )
                    }
                } catch (e: TimeoutCancellationException) {
                    AiApiResponse.failure(ApiErrorCode.AI_MODEL_TIMEOUT.name, modelSpec.vendor)
                } catch (e: AiServiceException) {
                    AiApiResponse.failure(e.errorCode.name, modelSpec.vendor)
                }

                val end = Instant.now()
                val took = Duration.between(start, end)
                logger().info(
                    "api call end={} took={}ms ({}s) vendor={} clientId={}",
                    end, took.toMillis(), "%.3f".format(took.toMillis() / 1000.0), modelSpec.vendor, clientId
                )
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
        commonResponseSchema: String?
    ): VendorSpecificOptions {

        return when (val vendorOptions = modelSpec.vendorOptions) {
            is VendorOptions.GeminiOptions -> VendorSpecificOptions(
                jsonSchema = commonResponseSchema,
                urlContexts = vendorOptions.urlContexts,
                enableGoogleSearch = vendorOptions.enableGoogleSearch,
                toolNames = vendorOptions.toolNames,
                useCachedContent = vendorOptions.useCachedContent,
                cachedContentName = vendorOptions.cachedContentName
            )

            is VendorOptions.OpenAIOptions -> VendorSpecificOptions(
                jsonSchema = commonResponseSchema,
                urlContexts = null,
                enableGoogleSearch = null,
                toolNames = vendorOptions.toolNames
            )

            is VendorOptions.AnthropicOptions -> VendorSpecificOptions(
                jsonSchema = null, // Anthropic은 Structured Output 미지원
                urlContexts = null,
                enableGoogleSearch = null,
                toolNames = vendorOptions.toolNames,
                cacheStrategy = vendorOptions.cacheStrategy,
                cacheTtl = vendorOptions.cacheTtl
            )

            is VendorOptions.GrokOptions -> VendorSpecificOptions(
                jsonSchema = commonResponseSchema,
                urlContexts = null,
                enableGoogleSearch = null,
                toolNames = vendorOptions.toolNames
            )

            null -> VendorSpecificOptions(
                jsonSchema = commonResponseSchema,
                urlContexts = null,
                enableGoogleSearch = null,
                toolNames = null
            )
        }
    }

    /**
     * 스트리밍 방식으로 AI 응답을 받습니다.
     * 여러 모델을 동시에 스트리밍하여 Flux로 병합하여 반환합니다.
     */
    suspend fun stream(
        aiApiRequest: AiApiRequest,
        clientId: Long,
    ): Flux<String> {
        val start = Instant.now()
        logger().info("api stream call start={}", start)

        if (aiApiRequest.models.isEmpty()) {
            throw AiServiceException(
                ApiErrorCode.AI_MODELS_EMPTY,
                "스트리밍 요청에는 최소 하나의 모델이 필요합니다."
            )
        }

        // messages가 있으면 messages를 사용, 없으면 systemPrompt 필수 확인
        if (aiApiRequest.messages.isEmpty()) {
            throw AiServiceException(
                ApiErrorCode.AI_SYSTEM_PROMPT_REQUIRED,
                "스트리밍 요청에는 messages가 필수입니다."
            )
        }

        // 각 모델별로 스트림 생성
        val streams = aiApiRequest.models.map { modelSpec ->
            val client = aiApiFactory.getClient(modelSpec.vendor)

            // 벤더별 옵션 추출
            val options = extractVendorOptions(
                modelSpec,
                aiApiRequest.responseSchema
            )

            // timeout 설정 (기본값: 120초)
            val timeoutMs = (aiApiRequest.timeoutSeconds ?: 120) * 1000L

            // 각 모델의 스트림에 벤더 정보를 포함하여 구분 가능하도록 함
            // 스트리밍의 경우 timeout은 Flux의 timeout 연산자로 처리
            client.stream(
                aiApiRequest.messages,
                clientId,
                aiApiRequest.sessionId,
                options.jsonSchema,
                options.urlContexts,
                options.enableGoogleSearch,
                options.toolNames,
                modelSpec.version,
                options.useCachedContent,
                options.cachedContentName,
                options.cacheStrategy,
                options.cacheTtl
            ).timeout(java.time.Duration.ofMillis(timeoutMs))
                .map { chunk -> "[${modelSpec.vendor}:${modelSpec.version}] $chunk" } // 벤더와 모델 정보 추가
                .doOnComplete {
                    logger().info("Stream completed for vendor: ${modelSpec.vendor}, model: ${modelSpec.version}, clientId: $clientId")
                }
                .doOnError { error ->
                    logger().error(
                        "Stream error for vendor: ${modelSpec.vendor}, model: ${modelSpec.version}, clientId: $clientId",
                        error
                    )
                }
        }

        // 모든 스트림을 병합하여 하나의 Flux로 반환
        val mergedStream = Flux.merge(streams)
            .doOnComplete {
                val end = Instant.now()
                val took = Duration.between(start, end)
                logger().info(
                    "All streams completed. Total time: {}ms ({}s) clientId={}",
                    took.toMillis(), "%.3f".format(took.toMillis() / 1000.0), clientId
                )
            }
            .doOnError { error ->
                logger().error("Merged stream error for clientId: $clientId", error)
            }

        return mergedStream
    }
}