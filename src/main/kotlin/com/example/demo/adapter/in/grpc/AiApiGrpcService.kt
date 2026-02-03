package com.example.demo.adapter.`in`.grpc

import com.example.demo.business.AiService
import com.example.demo.business.EmbeddingService
import com.example.demo.dto.ChatMessage
import com.example.demo.dto.EmbeddingApiRequest
import com.example.demo.dto.EmbeddingModelSpec
import com.example.demo.dto.ModelSpec
import com.example.demo.model.Vendor
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.runBlocking
import net.devh.boot.grpc.server.service.GrpcService
import org.woo.ai.grpc.AiApiServiceGrpc
import org.woo.ai.grpc.AiProto
import reactor.core.publisher.Mono

/**
 * gRPC AI API 서비스.
 * 호출 서비스에서 전달한 application_id로 API 키를 조회해 Chat/Embedding을 수행한다.
 */
@GrpcService
class AiApiGrpcService(
    private val aiService: AiService,
    private val embeddingService: EmbeddingService,
) : AiApiServiceGrpc.AiApiServiceImplBase() {

    override fun chat(request: AiProto.AiApiRequest, responseObserver: StreamObserver<AiProto.AiApiResponse>) {
        val applicationId = request.applicationId.ifBlank { null }
            ?: run {
                responseObserver.onError(IllegalArgumentException("application_id is required"))
                return
            }
        Mono.fromCallable {
            runBlocking {
                val dto = com.example.demo.dto.AiApiRequest(
                    applicationId = applicationId,
                    models = request.modelsList.map { protoToModelSpec(it) },
                    messages = request.messagesList.map { protoToChatMessage(it) },
                    sessionId = request.sessionId.ifBlank { "grpc" },
                    responseSchema = request.responseSchema.takeIf { it.isNotBlank() },
                    timeoutSeconds = if (request.hasTimeoutSeconds()) request.timeoutSeconds.value else null,
                )
                aiService.call(dto, applicationId)
            }
        }
            .flatMap { responses ->
                val first = responses.firstOrNull()
                if (first == null) {
                    Mono.error(IllegalStateException("No response"))
                } else {
                    Mono.just(
                        AiProto.AiApiResponse.newBuilder()
                            .setVendor(vendorToProto(first.vendor))
                            .setResult(first.result ?: "")
                            .setIsError(first.isError)
                            .build()
                    )
                }
            }
            .subscribe(
                { responseObserver.onNext(it); responseObserver.onCompleted() },
                { responseObserver.onError(it) }
            )
    }

    override fun embedding(request: AiProto.EmbeddingApiRequest, responseObserver: StreamObserver<AiProto.EmbeddingApiResponse>) {
        val applicationId = request.applicationId.ifBlank { null }
            ?: run {
                responseObserver.onError(IllegalArgumentException("application_id is required"))
                return
            }
        Mono.fromCallable {
            runBlocking {
                val dto = EmbeddingApiRequest(
                    applicationId = applicationId,
                    models = request.modelsList.map { protoToEmbeddingModelSpec(it) },
                    texts = request.textsList,
                )
                embeddingService.embed(dto, applicationId)
            }
        }
            .flatMap { responses ->
                val first = responses.firstOrNull()
                if (first == null) {
                    Mono.error(IllegalStateException("No response"))
                } else {
                    val embeddings = first.embeddings.map { values ->
                        AiProto.EmbeddingVector.newBuilder().addAllValues(values.toList()).build()
                    }
                    Mono.just(
                        AiProto.EmbeddingApiResponse.newBuilder()
                            .setVendor(vendorToProto(first.vendor))
                            .addAllEmbeddings(embeddings)
                            .setDimensions(first.dimensions ?: 0)
                            .setModel(first.model ?: "")
                            .build()
                    )
                }
            }
            .subscribe(
                { responseObserver.onNext(it); responseObserver.onCompleted() },
                { responseObserver.onError(it) }
            )
    }

    private fun protoToModelSpec(p: AiProto.ModelSpec): ModelSpec {
        val vendor = when (p.vendor) {
            AiProto.Vendor.OPENAI -> Vendor.OPENAI
            AiProto.Vendor.ANTHROPIC -> Vendor.ANTHROPIC
            AiProto.Vendor.GOOGLE -> Vendor.GOOGLE
            AiProto.Vendor.X_AI -> Vendor.X_AI
            else -> Vendor.OPENAI
        }
        val opts = p.vendorOptions
        val vendorOptions = if (opts == null) null else when (vendor) {
            Vendor.GOOGLE -> com.example.demo.dto.VendorOptions.GeminiOptions(
                urlContexts = opts.urlContextsList.ifEmpty { null }?.let { it.toList() },
                enableGoogleSearch = if (opts.hasEnableGoogleSearch()) opts.enableGoogleSearch else null,
                toolNames = opts.toolNamesList.ifEmpty { null }?.let { it.toList() },
                useCachedContent = if (opts.hasUseCachedContent()) opts.useCachedContent else null,
                cachedContentName = opts.cachedContentName.takeIf { it.isNotBlank() },
            )
            Vendor.OPENAI -> com.example.demo.dto.VendorOptions.OpenAIOptions(
                toolNames = opts.toolNamesList.ifEmpty { null }?.let { it.toList() },
            )
            Vendor.ANTHROPIC -> com.example.demo.dto.VendorOptions.AnthropicOptions(
                toolNames = opts.toolNamesList.ifEmpty { null }?.let { it.toList() },
                cacheStrategy = opts.cacheStrategy.takeIf { it.isNotBlank() },
                cacheTtl = opts.cacheTtl.takeIf { it.isNotBlank() },
            )
            Vendor.X_AI -> com.example.demo.dto.VendorOptions.GrokOptions(
                toolNames = opts.toolNamesList.ifEmpty { null }?.let { it.toList() },
            )
        }
        return ModelSpec(vendor = vendor, version = p.version, vendorOptions = vendorOptions)
    }

    private fun protoToChatMessage(p: AiProto.ChatMessage): ChatMessage =
        when (p.role.lowercase()) {
            "user" -> com.example.demo.dto.UserChatMessage(p.content)
            "system" -> com.example.demo.dto.SystemChatMessage(p.content)
            "assistant" -> com.example.demo.dto.AssistantChatMessage(p.content)
            else -> com.example.demo.dto.UserChatMessage(p.content)
        }

    private fun protoToEmbeddingModelSpec(p: AiProto.EmbeddingModelSpec): EmbeddingModelSpec {
        val vendor = when (p.vendor) {
            AiProto.Vendor.OPENAI -> Vendor.OPENAI
            AiProto.Vendor.ANTHROPIC -> Vendor.ANTHROPIC
            AiProto.Vendor.GOOGLE -> Vendor.GOOGLE
            AiProto.Vendor.X_AI -> Vendor.X_AI
            else -> Vendor.OPENAI
        }
        val opts = p.vendorOptions
        val vendorOptions = if (opts == null) null else when (vendor) {
            Vendor.OPENAI -> com.example.demo.dto.EmbeddingVendorOptions.OpenAIOptions(
                dimensions = if (opts.hasDimensions()) opts.dimensions.value else null,
                encodingFormat = opts.encodingFormat.takeIf { it.isNotBlank() },
                user = opts.user.takeIf { it.isNotBlank() },
            )
            Vendor.GOOGLE -> com.example.demo.dto.EmbeddingVendorOptions.GeminiOptions(
                taskType = opts.taskType.takeIf { it.isNotBlank() },
                dimensions = if (opts.hasDimensions()) opts.dimensions.value else null,
                title = opts.title.takeIf { it.isNotBlank() },
                autoTruncate = if (opts.hasAutoTruncate()) opts.autoTruncate else null,
            )
            Vendor.X_AI -> com.example.demo.dto.EmbeddingVendorOptions.GrokOptions(
                dimensions = if (opts.hasDimensions()) opts.dimensions.value else null,
                encodingFormat = opts.encodingFormat.takeIf { it.isNotBlank() },
                user = opts.user.takeIf { it.isNotBlank() },
            )
            else -> null
        }
        return EmbeddingModelSpec(vendor = vendor, version = p.version, vendorOptions = vendorOptions)
    }

    private fun vendorToProto(v: Vendor): AiProto.Vendor = when (v) {
        Vendor.OPENAI -> AiProto.Vendor.OPENAI
        Vendor.ANTHROPIC -> AiProto.Vendor.ANTHROPIC
        Vendor.GOOGLE -> AiProto.Vendor.GOOGLE
        Vendor.X_AI -> AiProto.Vendor.X_AI
        else -> AiProto.Vendor.VENDOR_UNSPECIFIED
    }
}
