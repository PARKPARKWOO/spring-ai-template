package com.example.demo.adapter.out.client

import com.example.demo.adapter.out.persistence.AiUsageLogsRepository
import com.example.demo.adapter.out.persistence.ClientApiKeyRepository
import com.example.demo.business.QuotaService
import com.example.demo.business.TokenizerService
import com.example.demo.common.logger
import com.example.demo.dto.AiApiResponse
import com.example.demo.model.AiUsageLogs
import com.example.demo.model.Vendor
import com.example.demo.model.api.ApiKey
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.prompt.Prompt

abstract class AbstractAiCallTemplate(
    private val tokenizerService: TokenizerService,
    private val quotaService: QuotaService,
    private val aiUsageLogsRepository: AiUsageLogsRepository,
    private val clientApiKeyRepository: ClientApiKeyRepository,
) : AiCallPort {
    abstract fun getVendor(): Vendor
    protected abstract fun call(prompt: Prompt, apiKey: ApiKey): ChatResponse

    // List 요청시 처리를 어떻게 하면 좋을지??
    // 토큰 오차율 최대 10% (앤트로픽 기준) 241/238
    override suspend fun call(prompt: Prompt, clientId: Long): AiApiResponse {
        val vendor = getVendor()
        quotaService.isQuotaExceeded(clientId, vendor)

        // clientId와 vendor로 사용 가능한 ApiKey 조회
        val clientApiKey = clientApiKeyRepository.findActiveApiKeyByClientIdAndVendor(clientId, vendor)
            ?: throw IllegalArgumentException("No active API key found for clientId: $clientId and vendor: $vendor")
        
        val apiKey = clientApiKey.apiKey
        logger().info("Using API key for clientId: $clientId, vendor: $vendor, apiKeyId: ${apiKey.id}")

        val response = call(prompt, apiKey)

        val inputToken = response.metadata.usage.promptTokens
        val outputToken = response.metadata.usage.completionTokens
        val totalToken = response.metadata.usage.totalTokens
        logger().info("total token count = $totalToken prompt Token = $inputToken outputToken = $outputToken")
        val tokenCount = tokenizerService.getTokenCount(prompt)
        logger().info("Received token count $tokenCount for $vendor")
        val result = response.result.output.text ?: "no content"
        val model = prompt.options?.model ?: "dummy"
        save(vendor, clientId, model, prompt.userMessage.text, result, inputToken, outputToken)
        return AiApiResponse(vendor, result)
    }

    private fun save(
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