package com.example.demo.dto

import com.example.demo.business.exception.AiServiceException
import com.example.demo.model.ApiErrorCode
import com.example.demo.model.Vendor
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

/**
 * ModelSpec을 역직렬화하는 커스텀 Deserializer
 * vendor 필드를 먼저 읽어서 vendorOptions를 적절한 서브타입으로 역직렬화합니다.
 */
class ModelSpecDeserializer : JsonDeserializer<ModelSpec>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): ModelSpec {
        val node: JsonNode = p.codec.readTree(p)
        val objectMapper = ctxt.parser.codec as ObjectMapper
        
        // vendor 필드 먼저 읽기
        val vendorNode = node.get("vendor")
        val vendor = if (vendorNode != null && vendorNode.isTextual) {
            try {
                Vendor.valueOf(vendorNode.asText())
            } catch (e: IllegalArgumentException) {
                throw AiServiceException(
                    ApiErrorCode.AI_REQUEST_VALIDATION_FAILED,
                    "유효하지 않은 vendor 값입니다: ${vendorNode.asText()}. 지원되는 값: ${Vendor.entries.joinToString { it.name }}"
                )
            }
        } else {
            throw AiServiceException(
                ApiErrorCode.AI_REQUEST_VALIDATION_FAILED,
                "vendor 필드는 필수이며 문자열이어야 합니다."
            )
        }
        
        // version 필드 읽기
        val versionNode = node.get("version")
        val version = if (versionNode != null && versionNode.isTextual) {
            versionNode.asText()
        } else {
            throw AiServiceException(
                ApiErrorCode.AI_REQUEST_VALIDATION_FAILED,
                "version 필드는 필수이며 문자열이어야 합니다."
            )
        }
        
        // vendorOptions 필드 읽기 (선택사항)
        val vendorOptionsNode = node.get("vendorOptions")
        val vendorOptions: VendorOptions? = if (vendorOptionsNode != null && !vendorOptionsNode.isNull && vendorOptionsNode.isObject) {
            // vendor에 따라 적절한 서브타입으로 역직렬화
            // treeToValue 대신 직접 JsonNode를 파싱하여 더 안전하게 처리
            when (vendor) {
                Vendor.GOOGLE -> {
                    val urlContexts = vendorOptionsNode.get("urlContexts")?.let { urlNode ->
                        if (urlNode.isArray) {
                            urlNode.mapNotNull { it.asText(null) }
                        } else null
                    }
                    val enableGoogleSearch = vendorOptionsNode.get("enableGoogleSearch")?.asBoolean()
                    val toolNames = vendorOptionsNode.get("toolNames")?.let { toolNode ->
                        if (toolNode.isArray) {
                            toolNode.mapNotNull { it.asText(null) }
                        } else null
                    }
                    VendorOptions.GeminiOptions(
                        urlContexts = urlContexts,
                        enableGoogleSearch = enableGoogleSearch,
                        toolNames = toolNames
                    )
                }
                Vendor.OPENAI -> {
                    val toolNames = vendorOptionsNode.get("toolNames")?.let { toolNode ->
                        if (toolNode.isArray) {
                            toolNode.mapNotNull { it.asText(null) }
                        } else null
                    }
                    VendorOptions.OpenAIOptions(toolNames = toolNames)
                }
                Vendor.ANTHROPIC -> {
                    val toolNames = vendorOptionsNode.get("toolNames")?.let { toolNode ->
                        if (toolNode.isArray) {
                            toolNode.mapNotNull { it.asText(null) }
                        } else null
                    }
                    VendorOptions.AnthropicOptions(toolNames = toolNames)
                }
                Vendor.X_AI -> {
                    val toolNames = vendorOptionsNode.get("toolNames")?.let { toolNode ->
                        if (toolNode.isArray) {
                            toolNode.mapNotNull { it.asText(null) }
                        } else null
                    }
                    VendorOptions.GrokOptions(toolNames = toolNames)
                }
            }
        } else {
            null
        }
        
        return ModelSpec(
            vendor = vendor,
            version = version,
            vendorOptions = vendorOptions
        )
    }
}
