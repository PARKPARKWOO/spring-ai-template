package com.example.demo.adapter.out.client

import com.example.demo.dto.AiApiResponse
import reactor.core.publisher.Flux

interface AiCallPort {
    suspend fun call(userMessage: String, clientId: Long, sessionId: String, model: String?): AiApiResponse

    suspend fun call(
        userMessage: String,
        systemPrompt: String,
        clientId: Long,
        sessionId: String,
        jsonSchema: String? = null,
        urlContexts: List<String>? = null,
        enableGoogleSearch: Boolean? = null,
        toolNames: List<String>? = null,
        model: String?,
        // Gemini 캐시 옵션
        useCachedContent: Boolean? = null,
        cachedContentName: String? = null,
        // Anthropic 캐시 옵션
        cacheStrategy: String? = null,
        cacheTtl: String? = null,
    ): AiApiResponse

    /**
     * 스트리밍 방식으로 AI 응답을 받습니다.
     * @return Flux<String> - 실시간으로 생성되는 텍스트 청크 스트림
     */
    suspend fun stream(
        userMessage: String,
        systemPrompt: String,
        clientId: Long,
        sessionId: String,
        jsonSchema: String? = null,
        urlContexts: List<String>? = null,
        enableGoogleSearch: Boolean? = null,
        toolNames: List<String>? = null,
        model: String?,
        // Gemini 캐시 옵션
        useCachedContent: Boolean? = null,
        cachedContentName: String? = null,
        // Anthropic 캐시 옵션
        cacheStrategy: String? = null,
        cacheTtl: String? = null,
    ): Flux<String>
}