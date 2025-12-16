package com.example.demo.adapter.out.client

import com.example.demo.dto.AiApiResponse
import com.example.demo.model.Vendor
import org.springframework.ai.chat.prompt.Prompt

interface AiCallPort {
    suspend fun call(prompt: Prompt, clientId: Long): AiApiResponse
    fun generatePrompt(userMessage: String, clientId: Long): Prompt
}