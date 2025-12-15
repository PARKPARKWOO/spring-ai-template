package com.example.demo.common

import org.springframework.ai.anthropic.api.AnthropicApi
import org.springframework.ai.model.ApiKey
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.api.OpenAiApi
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AiConfig (
    @Value("\${spring.ai.openai.api-key}")
    private val openAiApiKey: String,
    @Value("\${}")
    private val anthropicApiKey: String,
){
    @Bean
    fun openAi(): OpenAiApi = OpenAiApi.builder()
        .apiKey(openAiApiKey)
        .build()

//    @Bean
//    fun claude(): AnthropicApi = AnthropicApi.builder()
//        .apiKey(anthropicApiKey)
//        .build()
}