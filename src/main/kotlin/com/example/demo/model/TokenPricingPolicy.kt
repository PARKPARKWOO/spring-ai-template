package com.example.demo.model

import com.example.demo.model.Vendor
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Vendor + Model별 토큰 가격 정책
 * 각 모델마다 Input Token과 Output Token의 가격이 다름
 * 
 * clientId가 null이면 공통 정책, 값이 있으면 Client별 정책
 */
@Entity
@Table(name = "token_pricing_policy")
class TokenPricingPolicy(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private val id: Long,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private val vendor: Vendor,
    
    @Column(nullable = false, length = 255)
    private val model: String,
    
    /**
     * Client ID (nullable)
     * null이면 공통 정책, 값이 있으면 해당 Client의 전용 정책
     */
    @Column(name = "client_id", nullable = true)
    private val clientId: Long? = null,
    
    /**
     * Input Token 가격 (1M 토큰당 원화)
     * 예: 0.15 USD = 약 200원 (환율 1,333 기준)
     */
    @Column(nullable = false, precision = 20, scale = 8)
    private val inputTokenPricePerMillion: BigDecimal,
    
    /**
     * Output Token 가격 (1M 토큰당 원화)
     */
    @Column(nullable = false, precision = 20, scale = 8)
    private val outputTokenPricePerMillion: BigDecimal,
    
    @Column(nullable = false)
    private val isActive: Boolean = true,
    
    @Column(nullable = false)
    private val createdAt: LocalDateTime,
    
    @Column(nullable = false)
    private val updatedAt: LocalDateTime,
    
    private val deletedAt: LocalDateTime? = null,
) {
    companion object {
        /**
         * 공통 정책 생성 (clientId = null)
         */
        fun create(
            vendor: Vendor,
            model: String,
            inputTokenPricePerMillion: BigDecimal,
            outputTokenPricePerMillion: BigDecimal,
        ): TokenPricingPolicy {
            val now = LocalDateTime.now()
            return TokenPricingPolicy(
                id = 0L,
                vendor = vendor,
                model = model,
                clientId = null,
                inputTokenPricePerMillion = inputTokenPricePerMillion,
                outputTokenPricePerMillion = outputTokenPricePerMillion,
                isActive = true,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
            )
        }
        
        /**
         * Client별 정책 생성
         */
        fun createForClient(
            clientId: Long,
            vendor: Vendor,
            model: String,
            inputTokenPricePerMillion: BigDecimal,
            outputTokenPricePerMillion: BigDecimal,
        ): TokenPricingPolicy {
            val now = LocalDateTime.now()
            return TokenPricingPolicy(
                id = 0L,
                vendor = vendor,
                model = model,
                clientId = clientId,
                inputTokenPricePerMillion = inputTokenPricePerMillion,
                outputTokenPricePerMillion = outputTokenPricePerMillion,
                isActive = true,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
            )
        }
    }
    
    /**
     * 토큰 사용량을 기반으로 비용 계산
     * @param inputTokens Input 토큰 수
     * @param outputTokens Output 토큰 수
     * @return 계산된 비용 (원화)
     */
    fun calculateCost(inputTokens: Int, outputTokens: Int): BigDecimal {
        val inputCost = BigDecimal(inputTokens)
            .divide(BigDecimal(1_000_000), 8, java.math.RoundingMode.HALF_UP)
            .multiply(inputTokenPricePerMillion)
        
        val outputCost = BigDecimal(outputTokens)
            .divide(BigDecimal(1_000_000), 8, java.math.RoundingMode.HALF_UP)
            .multiply(outputTokenPricePerMillion)
        
        return inputCost.add(outputCost)
    }
    
    // Getter methods for external access
    fun getId(): Long = id
    fun getVendor(): Vendor = vendor
    fun getModel(): String = model
    fun getClientId(): Long? = clientId
    fun getInputTokenPricePerMillion(): BigDecimal = inputTokenPricePerMillion
    fun getOutputTokenPricePerMillion(): BigDecimal = outputTokenPricePerMillion
    fun getIsActive(): Boolean = isActive
    fun getCreatedAt(): LocalDateTime = createdAt
    fun getUpdatedAt(): LocalDateTime = updatedAt
    fun getDeletedAt(): LocalDateTime? = deletedAt
    
    /**
     * 공통 정책인지 확인
     */
    fun isCommonPolicy(): Boolean = clientId == null
    
    /**
     * Client별 정책인지 확인
     */
    fun isClientPolicy(): Boolean = clientId != null
}
