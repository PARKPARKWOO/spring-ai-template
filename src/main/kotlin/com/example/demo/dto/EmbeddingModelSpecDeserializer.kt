package com.example.demo.dto

import com.example.demo.business.exception.EmbeddingServiceException
import com.example.demo.model.ApiErrorCode
import com.example.demo.model.Vendor
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

/**
 * EmbeddingModelSpec을 역직렬화하는 커스텀 Deserializer
 * vendor 필드를 먼저 읽어서 vendorOptions를 적절한 서브타입으로 역직렬화합니다.
 */
class EmbeddingModelSpecDeserializer : JsonDeserializer<EmbeddingModelSpec>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): EmbeddingModelSpec {
        val node: JsonNode = p.codec.readTree(p)
        val objectMapper = ctxt.parser.codec as ObjectMapper

        // vendor 필드 먼저 읽기
        val vendorNode = node.get("vendor")
        val vendor = if (vendorNode != null && vendorNode.isTextual) {
            try {
                Vendor.valueOf(vendorNode.asText())
            } catch (e: IllegalArgumentException) {
                throw EmbeddingServiceException(
                    ApiErrorCode.EMBEDDING_REQUEST_VALIDATION_FAILED,
                    "유효하지 않은 vendor 값입니다: ${vendorNode.asText()}. 지원되는 값: ${Vendor.entries.joinToString { it.name }}"
                )
            }
        } else {
            throw EmbeddingServiceException(
                ApiErrorCode.EMBEDDING_REQUEST_VALIDATION_FAILED,
                "vendor 필드는 필수이며 문자열이어야 합니다."
            )
        }

        // version 필드 읽기
        val versionNode = node.get("version")
        val version = if (versionNode != null && versionNode.isTextual) {
            versionNode.asText()
        } else {
            throw EmbeddingServiceException(
                ApiErrorCode.EMBEDDING_REQUEST_VALIDATION_FAILED,
                "version 필드는 필수이며 문자열이어야 합니다."
            )
        }

        // vendorOptions 필드 읽기 (선택사항)
        val vendorOptionsNode = node.get("vendorOptions")
        val vendorOptions: EmbeddingVendorOptions? = if (vendorOptionsNode != null && !vendorOptionsNode.isNull && vendorOptionsNode.isObject) {
            // vendor에 따라 적절한 서브타입으로 역직렬화
            when (vendor) {
                Vendor.OPENAI -> {
                    val dimensions = vendorOptionsNode.get("dimensions")?.asInt()
                    val encodingFormat = vendorOptionsNode.get("encodingFormat")?.asText()
                    val user = vendorOptionsNode.get("user")?.asText()
                    EmbeddingVendorOptions.OpenAIOptions(dimensions, encodingFormat, user)
                }
                Vendor.GOOGLE -> {
                    val taskType = vendorOptionsNode.get("taskType")?.asText()
                    val dimensions = vendorOptionsNode.get("dimensions")?.asInt()
                    val title = vendorOptionsNode.get("title")?.asText()
                    val autoTruncate = vendorOptionsNode.get("autoTruncate")?.asBoolean()
                    EmbeddingVendorOptions.GeminiOptions(taskType, dimensions, title, autoTruncate)
                }
                Vendor.X_AI -> {
                    val dimensions = vendorOptionsNode.get("dimensions")?.asInt()
                    val encodingFormat = vendorOptionsNode.get("encodingFormat")?.asText()
                    val user = vendorOptionsNode.get("user")?.asText()
                    EmbeddingVendorOptions.GrokOptions(dimensions, encodingFormat, user)
                }
                Vendor.ANTHROPIC -> {
                    // Anthropic은 Embedding을 지원하지 않음
                    null
                }
            }
        } else {
            null
        }

        return EmbeddingModelSpec(
            vendor = vendor,
            version = version,
            vendorOptions = vendorOptions
        )
    }
}
