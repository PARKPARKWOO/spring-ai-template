package com.example.demo.business

import com.example.demo.adapter.out.persistence.ClientPricingQuotaRepository
import com.example.demo.adapter.out.persistence.ClientTokenQuotaRepository
import com.example.demo.business.exception.AiServiceException
import com.example.demo.model.ApiErrorCode
import com.example.demo.model.ClientPricingQuota
import com.example.demo.model.ClientTokenQuota
import com.example.demo.model.ContentType
import com.example.demo.model.QuotaPolicy
import com.example.demo.model.Vendor
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

/**
 * 통합 쿼터 관리 서비스
 * 가격 기반과 토큰 기반 쿼터를 정책 기반으로 분기 처리하여 관리
 */
@Service
class QuotaManagementService(
    private val clientPricingQuotaRepository: ClientPricingQuotaRepository,
    private val clientTokenQuotaRepository: ClientTokenQuotaRepository,
    private val tokenPricingService: TokenPricingService,
) {
    private val log = LoggerFactory.getLogger(QuotaManagementService::class.java)
    /**
     * 가격 기반 쿼터 할당
     */
    @Transactional
    fun allocatePricingQuota(clientId: Long, amount: BigDecimal) {
        val quotas = clientPricingQuotaRepository.findByClientIdWithLock(clientId)
        
        if (quotas.isEmpty()) {
            // 가격 기반 쿼터가 없으면 제한 없음
            return
        }
        
        // 모든 활성 쿼터에 대해 할당 가능 여부 확인
        val canAllocate = quotas.all { it.canAllocate(amount) }
        
        if (!canAllocate) {
            throw AiServiceException(
                ApiErrorCode.AI_QUOTA_EXCEEDED,
                "가격 기반 쿼터를 초과했습니다. (요청 금액: $amount 원)"
            )
        }
        
        // 모든 쿼터에 할당
        quotas.forEach { it.allocate(amount) }
        clientPricingQuotaRepository.saveAll(quotas)
    }
    
    /**
     * 토큰 기반 쿼터 할당
     */
    @Transactional
    fun allocateTokenQuota(
        clientId: Long,
        vendor: Vendor,
        model: String,
        contentType: ContentType,
        tokens: Long
    ) {
        val quotas = clientTokenQuotaRepository.findByClientIdAndVendorAndModelWithLock(clientId, vendor, model)
        
        if (quotas.isEmpty()) {
            // 토큰 기반 쿼터가 없으면 제한 없음
            return
        }
        
        // ContentType 필터링 (NULL이면 모든 타입에 적용)
        val applicableQuotas = quotas.filter { 
            // TODO: ContentType 필터링 로직 추가 (현재는 모든 쿼터에 적용)
            true
        }
        
        if (applicableQuotas.isEmpty()) {
            return
        }
        
        // 모든 활성 쿼터에 대해 할당 가능 여부 확인
        val canAllocate = applicableQuotas.all { it.canAllocate(tokens) }
        
        if (!canAllocate) {
            throw AiServiceException(
                ApiErrorCode.AI_QUOTA_EXCEEDED,
                "토큰 기반 쿼터를 초과했습니다. (요청 토큰: $tokens)"
            )
        }
        
        // 모든 쿼터에 할당
        applicableQuotas.forEach { it.allocate(tokens) }
        clientTokenQuotaRepository.saveAll(applicableQuotas)
    }
    
    /**
     * 클라이언트의 쿼터 정책 확인
     * @param clientId 클라이언트 ID
     * @param vendor AI 벤더
     * @param model 모델 이름
     * @param contentType 컨텐츠 타입 (기본값: TEXT)
     * @return QuotaPolicy (PRICING_ONLY, TOKEN_ONLY, BOTH, NONE)
     */
    @Transactional(readOnly = true)
    fun getQuotaPolicy(
        clientId: Long,
        vendor: Vendor,
        model: String,
        contentType: ContentType = ContentType.TEXT
    ): QuotaPolicy {
        val hasPricingQuota = clientPricingQuotaRepository.existsByClientIdAndIsActiveTrue(clientId)
        val hasTokenQuota = clientTokenQuotaRepository.existsByClientIdAndVendorAndModelAndIsActiveTrue(
            clientId, vendor, model
        )
        
        return QuotaPolicy.fromQuotas(hasPricingQuota, hasTokenQuota)
    }
    
    /**
     * 통합 할당 (정책 기반 분기 처리)
     */
    @Transactional
    fun allocateQuota(
        clientId: Long,
        vendor: Vendor,
        model: String,
        inputTokens: Int,
        outputTokens: Int,
        contentType: ContentType = ContentType.TEXT,
    ) {
        // 1. 클라이언트의 쿼터 정책 확인
        val policy = getQuotaPolicy(clientId, vendor, model, contentType)
        
        // 2. 정책에 따라 분기 처리
        when (policy) {
            QuotaPolicy.PRICING_ONLY -> {
                // 가격 기반만 검증 (Client별 정책 우선 적용)
                val estimatedCost = tokenPricingService.calculateCost(
                    vendor, model, clientId, contentType, inputTokens, outputTokens
                )
                allocatePricingQuota(clientId, estimatedCost)
                log.debug("Quota allocated (PRICING_ONLY) - clientId: $clientId, cost: $estimatedCost")
            }
            
            QuotaPolicy.TOKEN_ONLY -> {
                // 토큰 기반만 검증 (비용 계산 불필요)
                val totalTokens = inputTokens + outputTokens
                allocateTokenQuota(clientId, vendor, model, contentType, totalTokens.toLong())
                log.debug("Quota allocated (TOKEN_ONLY) - clientId: $clientId, tokens: $totalTokens")
            }
            
            QuotaPolicy.BOTH -> {
                // 둘 다 검증 (Client별 정책 우선 적용)
                val estimatedCost = tokenPricingService.calculateCost(
                    vendor, model, clientId, contentType, inputTokens, outputTokens
                )
                val totalTokens = inputTokens + outputTokens
                
                allocatePricingQuota(clientId, estimatedCost)
                allocateTokenQuota(clientId, vendor, model, contentType, totalTokens.toLong())
                log.debug("Quota allocated (BOTH) - clientId: $clientId, cost: $estimatedCost, tokens: $totalTokens")
            }
            
            QuotaPolicy.NONE -> {
                // 제한 없음 - 아무것도 하지 않음
                log.debug("Quota allocated (NONE) - clientId: $clientId, no quota restrictions")
            }
        }
    }
    
    /**
     * 실제 사용량으로 조정 (정책 기반 분기 처리)
     */
    @Transactional
    fun adjustByActualUsage(
        clientId: Long,
        vendor: Vendor,
        model: String,
        contentType: ContentType,
        allocatedInputTokens: Int,
        allocatedOutputTokens: Int,
        actualInputTokens: Int,
        actualOutputTokens: Int,
    ) {
        // 1. 클라이언트의 쿼터 정책 확인
        val policy = getQuotaPolicy(clientId, vendor, model, contentType)
        
        // 2. 정책에 따라 분기 처리
        when (policy) {
            QuotaPolicy.PRICING_ONLY -> {
                // 가격 기반만 조정 (Client별 정책 우선 적용)
                val allocatedCost = tokenPricingService.calculateCost(
                    vendor, model, clientId, contentType, allocatedInputTokens, allocatedOutputTokens
                )
                val actualCost = tokenPricingService.calculateCost(
                    vendor, model, clientId, contentType, actualInputTokens, actualOutputTokens
                )
                adjustPricingQuota(clientId, allocatedCost, actualCost)
                log.debug("Quota adjusted (PRICING_ONLY) - clientId: $clientId, allocated: $allocatedCost, actual: $actualCost")
            }
            
            QuotaPolicy.TOKEN_ONLY -> {
                // 토큰 기반만 조정
                val allocatedTotalTokens = allocatedInputTokens + allocatedOutputTokens
                val actualTotalTokens = actualInputTokens + actualOutputTokens
                adjustTokenQuota(clientId, vendor, model, contentType, allocatedTotalTokens.toLong(), actualTotalTokens.toLong())
                log.debug("Quota adjusted (TOKEN_ONLY) - clientId: $clientId, allocated: $allocatedTotalTokens, actual: $actualTotalTokens")
            }
            
            QuotaPolicy.BOTH -> {
                // 둘 다 조정 (Client별 정책 우선 적용)
                val allocatedCost = tokenPricingService.calculateCost(
                    vendor, model, clientId, contentType, allocatedInputTokens, allocatedOutputTokens
                )
                val actualCost = tokenPricingService.calculateCost(
                    vendor, model, clientId, contentType, actualInputTokens, actualOutputTokens
                )
                val allocatedTotalTokens = allocatedInputTokens + allocatedOutputTokens
                val actualTotalTokens = actualInputTokens + actualOutputTokens
                
                adjustPricingQuota(clientId, allocatedCost, actualCost)
                adjustTokenQuota(clientId, vendor, model, contentType, allocatedTotalTokens.toLong(), actualTotalTokens.toLong())
                log.debug("Quota adjusted (BOTH) - clientId: $clientId, cost: $allocatedCost->$actualCost, tokens: $allocatedTotalTokens->$actualTotalTokens")
            }
            
            QuotaPolicy.NONE -> {
                // 제한 없음 - 아무것도 하지 않음
                log.debug("Quota adjusted (NONE) - clientId: $clientId, no quota restrictions")
            }
        }
    }
    
    /**
     * 가격 기반 쿼터 조정 (내부 메서드)
     */
    private fun adjustPricingQuota(
        clientId: Long,
        allocatedCost: BigDecimal,
        actualCost: BigDecimal
    ) {
        val pricingQuotas = clientPricingQuotaRepository.findByClientIdWithLock(clientId)
        pricingQuotas.forEach { 
            it.adjustByActualUsage(allocatedCost, actualCost)
        }
        clientPricingQuotaRepository.saveAll(pricingQuotas)
    }
    
    /**
     * 토큰 기반 쿼터 조정 (내부 메서드)
     */
    private fun adjustTokenQuota(
        clientId: Long,
        vendor: Vendor,
        model: String,
        contentType: ContentType,
        allocatedTokens: Long,
        actualTokens: Long
    ) {
        val tokenQuotas = clientTokenQuotaRepository.findByClientIdAndVendorAndModelWithLock(clientId, vendor, model)
        // TODO: ContentType 필터링 로직 추가
        tokenQuotas.forEach {
            it.adjustByActualUsage(allocatedTokens, actualTokens)
        }
        clientTokenQuotaRepository.saveAll(tokenQuotas)
    }
    
    /**
     * 주기별 리셋 (Scheduler에서 호출)
     */
    @Transactional
    fun resetQuotas() {
        // 가격 기반 쿼터 리셋
        val pricingQuotas = clientPricingQuotaRepository.findQuotasNeedingReset()
        pricingQuotas.forEach { it.reset() }
        clientPricingQuotaRepository.saveAll(pricingQuotas)
        
        // 토큰 기반 쿼터 리셋
        val tokenQuotas = clientTokenQuotaRepository.findQuotasNeedingReset()
        tokenQuotas.forEach { it.reset() }
        clientTokenQuotaRepository.saveAll(tokenQuotas)
    }
}
