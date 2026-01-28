package com.example.demo.model

import com.example.demo.common.constants.CycleUnit
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Client별 가격 기반 제한
 * 예: Client 1은 월 30,000원 제한
 */
@Entity
@Table(name = "client_pricing_quota")
class ClientPricingQuota(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private val id: Long,
    
    @Column(nullable = false)
    private val clientId: Long,
    
    /**
     * 최대 금액 (원화)
     */
    @Column(nullable = false, precision = 20, scale = 2)
    private val maxAmount: BigDecimal,
    
    /**
     * 현재 사용 금액 (원화)
     */
    @Column(nullable = false, precision = 20, scale = 2)
    private var currentAmount: BigDecimal = BigDecimal.ZERO,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private val cycleUnit: CycleUnit,
    
    @Column(nullable = false)
    private var cycleStartedAt: LocalDateTime,
    
    @Column(nullable = false)
    private var nextResetAt: LocalDateTime,
    
    @Column(nullable = false)
    private val isActive: Boolean = true,
    
    @Column(nullable = false)
    private val createdAt: LocalDateTime,
    
    @Column(nullable = false)
    private var updatedAt: LocalDateTime,
    
    private val deletedAt: LocalDateTime? = null,
) {
    companion object {
        fun create(
            clientId: Long,
            maxAmount: BigDecimal,
            cycleUnit: CycleUnit,
        ): ClientPricingQuota {
            val now = LocalDateTime.now()
            val chronoUnit = cycleUnit.toChronoUnit()
            return ClientPricingQuota(
                id = 0L,
                clientId = clientId,
                maxAmount = maxAmount,
                currentAmount = BigDecimal.ZERO,
                cycleUnit = cycleUnit,
                cycleStartedAt = now,
                nextResetAt = now.plus(1, chronoUnit),
                isActive = true,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
            )
        }
    }
    
    /**
     * 비용 할당 가능 여부 확인
     * @param amount 할당하려는 비용
     * @return 할당 가능 여부
     */
    fun canAllocate(amount: BigDecimal): Boolean {
        if (!isActive) return false
        return currentAmount.add(amount) <= maxAmount
    }
    
    /**
     * 비용 할당
     * @param amount 할당할 비용
     * @throws IllegalStateException 할당 불가능한 경우
     */
    fun allocate(amount: BigDecimal) {
        if (!canAllocate(amount)) {
            throw IllegalStateException("가격 기반 쿼터를 초과했습니다. (현재: $currentAmount, 최대: $maxAmount, 요청: $amount)")
        }
        currentAmount = currentAmount.add(amount)
        updatedAt = LocalDateTime.now()
    }
    
    /**
     * 실제 사용 비용으로 조정
     * @param allocatedAmount 예약된 비용
     * @param actualAmount 실제 사용 비용
     */
    fun adjustByActualUsage(allocatedAmount: BigDecimal, actualAmount: BigDecimal) {
        val difference = actualAmount.subtract(allocatedAmount)
        currentAmount = currentAmount.add(difference)
        updatedAt = LocalDateTime.now()
    }
    
    /**
     * 주기 리셋
     */
    fun reset() {
        currentAmount = BigDecimal.ZERO
        cycleStartedAt = LocalDateTime.now()
        val chronoUnit = cycleUnit.toChronoUnit()
        nextResetAt = cycleStartedAt.plus(1, chronoUnit)
        updatedAt = LocalDateTime.now()
    }
    
    /**
     * 리셋 필요 여부 확인
     */
    fun needsReset(): Boolean {
        return LocalDateTime.now().isAfter(nextResetAt) || LocalDateTime.now().isEqual(nextResetAt)
    }
    
    // Getter methods for external access
    fun getId(): Long = id
    fun getClientId(): Long = clientId
    fun getMaxAmount(): BigDecimal = maxAmount
    fun getCurrentAmount(): BigDecimal = currentAmount
    fun getCycleUnit(): CycleUnit = cycleUnit
    fun getCycleStartedAt(): LocalDateTime = cycleStartedAt
    fun getNextResetAt(): LocalDateTime = nextResetAt
    fun getIsActive(): Boolean = isActive
    fun getCreatedAt(): LocalDateTime = createdAt
    fun getUpdatedAt(): LocalDateTime = updatedAt
    fun getDeletedAt(): LocalDateTime? = deletedAt
}
