package com.example.demo.business

import com.example.demo.adapter.out.persistence.TokenPricingPolicyRepository
import com.example.demo.business.exception.AiServiceException
import com.example.demo.model.ApiErrorCode
import com.example.demo.model.ContentType
import com.example.demo.model.TokenPricingPolicy
import com.example.demo.model.Vendor
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

/**
 * 토큰 가격 정책 관리 서비스
 */
@Service
class TokenPricingService(
    private val tokenPricingPolicyRepository: TokenPricingPolicyRepository,
) {
    /**
     * Vendor와 Model로 가격 정책 조회
     * clientId가 제공되면 Client별 정책을 우선 조회, 없으면 공통 정책 조회
     * 
     * @param vendor AI 벤더
     * @param model 모델 이름
     * @param clientId Client ID (선택사항, null이면 공통 정책만 조회)
     * @param contentType 컨텐츠 타입 (현재는 TEXT만 지원)
     * @return 가격 정책
     */
    @Transactional(readOnly = true)
    fun getPricingPolicy(
        vendor: Vendor,
        model: String,
        clientId: Long? = null,
        contentType: ContentType = ContentType.TEXT
    ): TokenPricingPolicy {
        // TODO: ContentType을 고려한 정책 조회 (현재는 TEXT만 지원)
        
        // clientId가 제공되면 우선순위 조회 (Client별 > 공통)
        if (clientId != null) {
            val policy = tokenPricingPolicyRepository.findBestMatchPolicy(clientId, vendor, model)
            if (policy != null) {
                return policy
            }
        }
        
        // 공통 정책 조회
        return tokenPricingPolicyRepository.findByVendorAndModelAndClientIdIsNullAndIsActiveTrueAndDeletedAtIsNull(vendor, model)
            ?: throw AiServiceException(
                ApiErrorCode.AI_REQUEST_VALIDATION_FAILED,
                "$vendor 벤더의 $model 모델에 대한 가격 정책을 찾을 수 없습니다."
            )
    }
    
    /**
     * 토큰 사용량을 기반으로 비용 계산
     * 
     * @param vendor AI 벤더
     * @param model 모델 이름
     * @param clientId Client ID (선택사항, null이면 공통 정책 사용)
     * @param contentType 컨텐츠 타입 (현재는 TEXT만 지원)
     * @param inputTokens Input 토큰 수
     * @param outputTokens Output 토큰 수
     * @return 계산된 비용 (원화)
     */
    fun calculateCost(
        vendor: Vendor,
        model: String,
        clientId: Long? = null,
        contentType: ContentType = ContentType.TEXT,
        inputTokens: Int,
        outputTokens: Int
    ): BigDecimal {
        val policy = getPricingPolicy(vendor, model, clientId, contentType)
        return policy.calculateCost(inputTokens, outputTokens)
    }
}
