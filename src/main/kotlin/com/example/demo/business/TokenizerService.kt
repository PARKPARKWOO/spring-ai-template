package com.example.demo.business

import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator
import org.springframework.stereotype.Service

@Service
class TokenizerService {
    private val tokenCountEstimator = JTokkitTokenCountEstimator()

    fun getTokenCount(userPrompt: String, systemPrompt: String): Int {
        return tokenCountEstimator.estimate(userPrompt + systemPrompt)
    }
}