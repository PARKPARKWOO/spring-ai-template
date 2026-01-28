package com.example.demo.model

import com.example.demo.common.constants.CycleUnit
import com.example.demo.model.Vendor
import jakarta.persistence.*
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Client별 토큰 기반 제한
 * 예: Client 1은 1일 최대 1,000,000 토큰, 한달 최대 10,000,000 토큰
 * Vendor/Model별로 세분화 가능 (NULL이면 전체)
 */
@Entity
@Table(name = "client_token_quota")
class ClientTokenQuota(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private val id: Long,
    
    @Column(nullable = false)
    private val clientId: Long,
    
    /**
     * Vendor (NULL이면 모든 벤더)
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private val vendor: Vendor? = null,
    
    /**
     * Model (NULL이면 모든 모델)
     */
    @Column(length = 255)
    private val model: String? = null,
    
    /**
     * 최대 토큰 수
     */
    @Column(nullable = false)
    private val maxTokens: Long,
    
    /**
     * 현재 사용 토큰 수
     */
    @Column(nullable = false)
    private var currentTokens: Long = 0,
    
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
            maxTokens: Long,
            cycleUnit: CycleUnit,
            vendor: Vendor? = null,
            model: String? = null,
        ): ClientTokenQuota {
            val now = LocalDateTime.now()
            val chronoUnit = cycleUnit.toChronoUnit()
            return ClientTokenQuota(
                id = 0L,
                clientId = clientId,
                vendor = vendor,
                model = model,
                maxTokens = maxTokens,
                currentTokens = 0,
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
     * 토큰 할당 가능 여부 확인
     * @param tokens 할당하려는 토큰 수
     * @return 할당 가능 여부
     */
    fun canAllocate(tokens: Long): Boolean {
        if (!isActive) return false
        return currentTokens + tokens <= maxTokens
    }
    
    /**
     * 토큰 할당
     * @param tokens 할당할 토큰 수
     * @throws IllegalStateException 할당 불가능한 경우
     */
    fun allocate(tokens: Long) {
        if (!canAllocate(tokens)) {
            throw IllegalStateException("토큰 기반 쿼터를 초과했습니다. (현재: $currentTokens, 최대: $maxTokens, 요청: $tokens)")
        }
        currentTokens += tokens
        updatedAt = LocalDateTime.now()
    }
    
    /**
     * 실제 사용 토큰으로 조정
     * @param allocatedTokens 예약된 토큰 수
     * @param actualTokens 실제 사용 토큰 수
     */
    fun adjustByActualUsage(allocatedTokens: Long, actualTokens: Long) {
        val difference = actualTokens - allocatedTokens
        currentTokens += difference
        updatedAt = LocalDateTime.now()
    }
    
    /**
     * 주기 리셋
     */
    fun reset() {
        currentTokens = 0
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
    
    /**
     * 특정 Vendor/Model과 매칭되는지 확인
     */
    fun matches(vendor: Vendor, model: String): Boolean {
        val vendorMatches = this.vendor == null || this.vendor == vendor
        val modelMatches = this.model == null || this.model == model
        return vendorMatches && modelMatches
    }
    
    // Getter methods for external access
    fun getId(): Long = id
    fun getClientId(): Long = clientId
    fun getVendor(): Vendor? = vendor
    fun getModel(): String? = model
    fun getMaxTokens(): Long = maxTokens
    fun getCurrentTokens(): Long = currentTokens
    fun getCycleUnit(): CycleUnit = cycleUnit
    fun getCycleStartedAt(): LocalDateTime = cycleStartedAt
    fun getNextResetAt(): LocalDateTime = nextResetAt
    fun getIsActive(): Boolean = isActive
    fun getCreatedAt(): LocalDateTime = createdAt
    fun getUpdatedAt(): LocalDateTime = updatedAt
    fun getDeletedAt(): LocalDateTime? = deletedAt
}
