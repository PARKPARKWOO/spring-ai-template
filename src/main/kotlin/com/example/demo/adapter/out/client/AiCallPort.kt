package com.example.demo.adapter.out.client

import com.example.demo.dto.AiApiResponse
import com.example.demo.dto.ChatMessage
import reactor.core.publisher.Flux

interface AiCallPort {
    suspend fun call(userMessage: String, applicationId: String, sessionId: String, model: String?): AiApiResponse

    suspend fun call(
        userMessage: String,
        systemPrompt: String,
        applicationId: String,
        sessionId: String,
        jsonSchema: String? = null,
        urlContexts: List<String>? = null,
        enableGoogleSearch: Boolean? = null,
        toolNames: List<String>? = null,
        model: String?,
        useCachedContent: Boolean? = null,
        cachedContentName: String? = null,
        cacheStrategy: String? = null,
        cacheTtl: String? = null,
    ): AiApiResponse

    suspend fun call(
        messages: List<ChatMessage>,
        applicationId: String,
        sessionId: String,
        jsonSchema: String? = null,
        urlContexts: List<String>? = null,
        enableGoogleSearch: Boolean? = null,
        toolNames: List<String>? = null,
        model: String?,
        useCachedContent: Boolean? = null,
        cachedContentName: String? = null,
        cacheStrategy: String? = null,
        cacheTtl: String? = null,
    ): AiApiResponse

    suspend fun stream(
        userMessage: String,
        systemPrompt: String,
        applicationId: String,
        sessionId: String,
        jsonSchema: String? = null,
        urlContexts: List<String>? = null,
        enableGoogleSearch: Boolean? = null,
        toolNames: List<String>? = null,
        model: String?,
        useCachedContent: Boolean? = null,
        cachedContentName: String? = null,
        cacheStrategy: String? = null,
        cacheTtl: String? = null,
    ): Flux<String>

    suspend fun stream(
        messages: List<ChatMessage>,
        applicationId: String,
        sessionId: String,
        jsonSchema: String? = null,
        urlContexts: List<String>? = null,
        enableGoogleSearch: Boolean? = null,
        toolNames: List<String>? = null,
        model: String?,
        useCachedContent: Boolean? = null,
        cachedContentName: String? = null,
        cacheStrategy: String? = null,
        cacheTtl: String? = null,
    ): Flux<String>
}
