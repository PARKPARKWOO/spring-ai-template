package com.example.demo.adapter.client

import org.springframework.ai.chat.prompt.Prompt

interface AiCallPort {
    suspend fun call(prompt: Prompt, clientId: Long)
    fun generatePrompt(userMessage: String, clientId: Long): Prompt
}