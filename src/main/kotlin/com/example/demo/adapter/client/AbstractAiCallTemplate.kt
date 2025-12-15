package com.example.demo.adapter.client

import com.example.demo.adapter.persistence.AiUsageLogsRepository
import com.example.demo.business.QuotaService
import com.example.demo.business.TokenizerService
import com.example.demo.common.logger
import com.example.demo.model.AiUsageLogs
import com.example.demo.model.Vendor
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.prompt.Prompt

abstract class AbstractAiCallTemplate(
    private val tokenizerService: TokenizerService,
    private val quotaService: QuotaService,
    private val aiUsageLogsRepository: AiUsageLogsRepository,
) : AiCallPort {
    abstract fun getVendor(): Vendor
    protected abstract fun call(prompt: Prompt): ChatResponse

    // List 요청시 처리를 어떻게 하면 좋을지??
    override suspend fun call(prompt: Prompt, clientId: Long) {
        val vendor = getVendor()
        quotaService.isQuotaExceeded(clientId, vendor)

        val response = call(prompt)

        val inputToken = response.metadata.usage.promptTokens
        val outputToken = response.metadata.usage.completionTokens
        val totalToken = response.metadata.usage.totalTokens
        logger().info("total token count $totalToken")
        val tokenCount = tokenizerService.getTokenCount(prompt)
        logger().info("Received token count $tokenCount for $vendor")
        response.results.map { result ->
            result.output
        }
    }

    fun save(
        vendor: Vendor,
        clientId: Long,
        model: String,
        requestMessage: String,
        responseMessage: String,
        promptToken: Int,
        completionToken: Int,
    ) {
        val logs = AiUsageLogs.create(
            vendor = vendor,
            clientId = clientId,
            requestMessage = requestMessage,
            responseMessage = responseMessage,
            model = model,
            promptToken = promptToken,
            completionToken = completionToken,
        )
        aiUsageLogsRepository.save(logs)
    }
}