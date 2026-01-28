package com.example.demo.business

import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.openai.api.OpenAiApi
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator
import org.springframework.stereotype.Service

@Service
class TokenizerService(
    private val openAiApi: OpenAiApi,
) {
    fun getTokenCount(userPrompt: String, systemPrompt: String): Int {
        return JTokkitTokenCountEstimator().estimate(userPrompt + systemPrompt)
    }
}