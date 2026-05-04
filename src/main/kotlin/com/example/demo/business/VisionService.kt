package com.example.demo.business

import com.example.demo.adapter.out.client.VisionFactory
import com.example.demo.business.exception.AiServiceException
import com.example.demo.dto.AiCallContext
import com.example.demo.dto.ModelSpec
import com.example.demo.dto.VisionRequest
import com.example.demo.dto.VisionResponse
import com.example.demo.model.ApiErrorCode
import com.example.demo.model.Vendor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class VisionService(
    private val visionFactory: VisionFactory,
) {
    private val log = LoggerFactory.getLogger(VisionService::class.java)

    suspend fun call(
        request: VisionRequest,
        applicationId: String,
    ): List<VisionResponse> = coroutineScope {
        if (request.messages.isEmpty()) {
            throw AiServiceException(
                ApiErrorCode.AI_SYSTEM_PROMPT_REQUIRED,
                "Vision 요청에는 messages 가 필수입니다.",
            )
        }
        if (request.images.isEmpty()) {
            throw AiServiceException(
                ApiErrorCode.AI_REQUEST_VALIDATION_FAILED,
                "Vision 요청에는 이미지가 하나 이상 필요합니다.",
            )
        }
        if (request.models.isEmpty()) {
            throw AiServiceException(
                ApiErrorCode.AI_MODELS_EMPTY,
                "models 가 비어있습니다.",
            )
        }

        if (request.fallback) {
            listOf(callSequentialFallback(request, applicationId))
        } else {
            request.models
                .map { modelSpec ->
                    async(Dispatchers.IO) { callSingle(request, applicationId, modelSpec) }
                }
                .awaitAll()
        }
    }

    private suspend fun callSequentialFallback(
        request: VisionRequest,
        applicationId: String,
    ): VisionResponse {
        var lastError: VisionResponse? = null
        request.models.forEachIndexed { index, modelSpec ->
            val response = callSingle(request, applicationId, modelSpec)
            if (!response.isError) {
                if (index > 0) {
                    log.info(
                        "Vision fallback succeeded at model index={} vendor={} model={} applicationId={}",
                        index, modelSpec.vendor, modelSpec.version, applicationId,
                    )
                }
                return response.copy(usedVendor = modelSpec.vendor, usedModel = modelSpec.version)
            }
            log.warn(
                "Vision fallback model index={} failed vendor={} model={} message={}",
                index, modelSpec.vendor, modelSpec.version, response.result,
            )
            lastError = response.copy(usedVendor = modelSpec.vendor, usedModel = modelSpec.version)
        }
        return lastError
            ?: VisionResponse.failure(ApiErrorCode.AI_MODELS_EMPTY.name, request.models.firstOrNull()?.vendor ?: Vendor.GOOGLE)
    }

    private suspend fun callSingle(
        request: VisionRequest,
        applicationId: String,
        modelSpec: ModelSpec,
    ): VisionResponse {
        val start = Instant.now()
        log.info(
            "vision call start={} vendor={} model={} requestType={} imageCount={} fallback={}",
            start, modelSpec.vendor, modelSpec.version, request.requestType, request.images.size, request.fallback,
        )

        return try {
            // visionFactory.getClient 도 try 블록 안에서 실행 — vendor 미지원(AI_VENDOR_NOT_SUPPORTED)
            // 같은 throw 가 forEach 를 break-out 시키지 않게 한다 (이전 핫픽스의 후속).
            val client = visionFactory.getClient(modelSpec.vendor)
            val context = AiCallContext(
                messages = request.messages,
                applicationId = applicationId,
                sessionId = request.sessionId,
                maxTokens = request.maxTokens,
                jsonSchema = null,
                model = modelSpec.version,
                vendorOptions = modelSpec.vendorOptions,
                requestType = request.requestType,
                timeoutSeconds = request.timeoutSeconds,
            )
            withTimeout(context.effectiveTimeoutMs()) {
                client.call(context, request.images)
            }
        } catch (e: TimeoutCancellationException) {
            log.warn("vision call timeout vendor={} model={}", modelSpec.vendor, modelSpec.version)
            VisionResponse.failure("timeout", modelSpec.vendor)
        } catch (e: Throwable) {
            // 어떤 예외든 VisionResponse.failure 로 변환해야 callSequentialFallback 의 forEach 가
            // 다음 모델로 넘어갈 수 있음.
            log.warn(
                "vision call failed vendor={} model={} err={}",
                modelSpec.vendor, modelSpec.version, e.message,
            )
            VisionResponse.failure(e.message ?: "vision call failed", modelSpec.vendor)
        }
    }
}
