package com.example.demo.business

import com.example.demo.adapter.out.client.AiApiFactory
import com.example.demo.business.exception.AiServiceException
import org.slf4j.LoggerFactory
import com.example.demo.dto.AiApiRequest
import com.example.demo.dto.AiApiResponse
import com.example.demo.dto.AiCallContext
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
                        log.info("api call start={} vendor={} model={} requestType={}", start, modelSpec.vendor, modelSpec.version, aiApiRequest.requestType)
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
                        response
                    }
                }.awaitAll()
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
