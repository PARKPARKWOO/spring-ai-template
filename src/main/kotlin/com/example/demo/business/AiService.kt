package com.example.demo.business

import com.example.demo.adapter.out.client.AiApiFactory
import com.example.demo.business.exception.AiServiceException
import org.slf4j.LoggerFactory
import com.example.demo.dto.AiApiRequest
import com.example.demo.dto.AiApiResponse
import com.example.demo.dto.AiCallContext
import com.example.demo.dto.ModelSpec
import com.example.demo.model.ApiErrorCode
import com.example.demo.model.Vendor
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

            // fallback=true: list 순차 시도, 첫 성공 응답 반환. 마지막까지 실패면 마지막 에러 응답.
            // fallback=false (기본): 기존 fan-out — 모든 모델 병렬 호출 + 응답 병합 (호환 유지).
            if (aiApiRequest.fallback) {
                listOf(callSequentialFallback(aiApiRequest, applicationId))
            } else {
                aiApiRequest.models
                    .map { modelSpec -> async(Dispatchers.IO) { callSingle(aiApiRequest, applicationId, modelSpec) } }
                    .awaitAll()
            }
        }

    /**
     * Fallback 흐름. models 리스트를 순차로 시도하고 첫 non-error 응답을 반환한다.
     * 모든 모델이 실패하면 마지막 에러 응답을 반환 (호출자가 isError 로 판단).
     */
    private suspend fun callSequentialFallback(
        aiApiRequest: AiApiRequest,
        applicationId: String,
    ): AiApiResponse {
        var lastError: AiApiResponse? = null
        aiApiRequest.models.forEachIndexed { index, modelSpec ->
            val response = callSingle(aiApiRequest, applicationId, modelSpec)
            if (!response.isError) {
                if (index > 0) {
                    log.info(
                        "Fallback succeeded at model index={} vendor={} model={} applicationId={}",
                        index, modelSpec.vendor, modelSpec.version, applicationId,
                    )
                }
                return response.copy(usedVendor = modelSpec.vendor, usedModel = modelSpec.version)
            }
            log.warn(
                "Fallback model index={} failed vendor={} model={} message={}",
                index, modelSpec.vendor, modelSpec.version, response.result,
            )
            lastError = response.copy(usedVendor = modelSpec.vendor, usedModel = modelSpec.version)
        }
        return lastError
            ?: AiApiResponse.failure(ApiErrorCode.AI_MODELS_EMPTY.name, aiApiRequest.models.firstOrNull()?.vendor ?: Vendor.GOOGLE)
    }

    private suspend fun callSingle(
        aiApiRequest: AiApiRequest,
        applicationId: String,
        modelSpec: ModelSpec,
    ): AiApiResponse {
        val start = Instant.now()
        log.info("api call start={} vendor={} model={} requestType={} fallback={}", start, modelSpec.vendor, modelSpec.version, aiApiRequest.requestType, aiApiRequest.fallback)
        val client = aiApiFactory.getClient(modelSpec.vendor)
        val context = AiCallContext.from(aiApiRequest, applicationId, modelSpec, aiApiRequest.responseSchema)
        val timeoutMs = context.effectiveTimeoutMs()

        val response =
            try {
                withTimeout(timeoutMs) {
                    client.call(context)
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
        return response
    }

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
            val context = AiCallContext.from(aiApiRequest, applicationId, modelSpec, aiApiRequest.responseSchema)
            val timeoutMs = context.effectiveTimeoutMs()
            client
                .stream(context)
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
