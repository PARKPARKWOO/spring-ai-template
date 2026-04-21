package com.example.demo.business

import com.example.demo.adapter.out.client.VisionFactory
import com.example.demo.business.exception.AiServiceException
import com.example.demo.dto.AiCallContext
import com.example.demo.dto.ModelSpec
import com.example.demo.dto.VisionRequest
import com.example.demo.dto.VisionResponse
import com.example.demo.model.ApiErrorCode
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

        request.models
            .map { modelSpec ->
                async(Dispatchers.IO) { callSingle(request, applicationId, modelSpec) }
            }
            .awaitAll()
    }

    private suspend fun callSingle(
        request: VisionRequest,
        applicationId: String,
        modelSpec: ModelSpec,
    ): VisionResponse {
        val start = Instant.now()
        log.info(
            "vision call start={} vendor={} model={} requestType={} imageCount={}",
            start, modelSpec.vendor, modelSpec.version, request.requestType, request.images.size,
        )

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

        return try {
            withTimeout(context.effectiveTimeoutMs()) {
                client.call(context, request.images)
            }
        } catch (e: TimeoutCancellationException) {
            log.warn("vision call timeout vendor={} model={}", modelSpec.vendor, modelSpec.version)
            VisionResponse.failure("timeout", modelSpec.vendor)
        }
    }
}
