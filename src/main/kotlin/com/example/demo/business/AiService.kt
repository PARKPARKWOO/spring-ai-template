package com.example.demo.business

import com.example.demo.adapter.out.client.AiApiFactory
import com.example.demo.dto.AiApiRequest
import com.example.demo.dto.AiApiResponse
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.stereotype.Service

@Service
class AiService(
    private val aiApiFactory: AiApiFactory,
) {
    suspend fun call(
        aiApiRequest: AiApiRequest,
        clientId: Long,
    ): List<AiApiResponse> = coroutineScope {
        val systemMessage = SystemMessage("이 응답에 대해서 맞고 틀리는 부분에 대해서 검증해줘 만약 틀렸다면 어디가 왜 틀렸는지 증명해")
        val userMessage = UserMessage(aiApiRequest.userPrompt)
        val prompt = Prompt(userMessage, systemMessage)
        aiApiRequest.vendor.map { vendor ->
            async {
                val client = aiApiFactory.getClient(vendor)
                client.call(prompt, clientId)
            }
        }.awaitAll()
    }
}