package com.example.demo.dto

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

/**
 * JSON Schema를 역직렬화하는 커스텀 Deserializer
 * JSON 객체와 JSON 문자열 모두를 처리합니다.
 */
class JsonSchemaDeserializer : JsonDeserializer<String>() {
    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): String? {
        val codec = parser.codec
        val node: JsonNode = codec.readTree(parser)
        
        // null이면 null 반환
        if (node.isNull) {
            return null
        }
        
        // 이미 문자열이면 그대로 반환
        if (node.isTextual) {
            return node.asText()
        }
        
        // JSON 객체면 문자열로 직렬화
        if (node.isObject || node.isArray) {
            val objectMapper = ObjectMapper()
            return objectMapper.writeValueAsString(node)
        }
        
        // 그 외의 경우는 문자열로 변환
        return node.toString()
    }
}
