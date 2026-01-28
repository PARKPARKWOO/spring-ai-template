package com.example.demo.business

import com.example.demo.adapter.out.persistence.TokenPricingPolicyRepository
import com.example.demo.business.exception.AiServiceException
import com.example.demo.model.ApiErrorCode
import com.example.demo.model.ContentType
import com.example.demo.model.TokenPricingPolicy
import com.example.demo.model.Vendor
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TokenPricingServiceTest {

    @Autowired
    private lateinit var tokenPricingService: TokenPricingService

    @Autowired
    private lateinit var tokenPricingPolicyRepository: TokenPricingPolicyRepository

    @BeforeEach
    fun setUp() {
        // 테스트용 공통 가격 정책 생성
        val commonPolicy = TokenPricingPolicy.create(
            vendor = Vendor.OPENAI,
            model = "gpt-4o-mini",
            inputTokenPricePerMillion = BigDecimal("100.0"),
            outputTokenPricePerMillion = BigDecimal("300.0")
        )
        tokenPricingPolicyRepository.save(commonPolicy)
    }

    @Test
    fun `가격 정책 조회 - 공통 정책`() {
        // when
        val policy = tokenPricingService.getPricingPolicy(
            vendor = Vendor.OPENAI,
            model = "gpt-4o-mini",
            clientId = null
        )

        // then
        assertNotNull(policy)
        assertEquals(Vendor.OPENAI, policy.getVendor())
        assertEquals("gpt-4o-mini", policy.getModel())
        assertNull(policy.getClientId())
        assertEquals(BigDecimal("100.0"), policy.getInputTokenPricePerMillion())
        assertEquals(BigDecimal("300.0"), policy.getOutputTokenPricePerMillion())
    }

    @Test
    fun `가격 정책 조회 - Client별 정책 우선`() {
        // given
        val clientId = 1L
        val clientPolicy = TokenPricingPolicy.createForClient(
            clientId = clientId,
            vendor = Vendor.OPENAI,
            model = "gpt-4o-mini",
            inputTokenPricePerMillion = BigDecimal("50.0"), // 공통보다 저렴
            outputTokenPricePerMillion = BigDecimal("150.0")
        )
        tokenPricingPolicyRepository.save(clientPolicy)

        // when
        val policy = tokenPricingService.getPricingPolicy(
            vendor = Vendor.OPENAI,
            model = "gpt-4o-mini",
            clientId = clientId
        )

        // then
        assertNotNull(policy)
        assertEquals(clientId, policy.getClientId())
        assertEquals(BigDecimal("50.0"), policy.getInputTokenPricePerMillion()) // Client별 정책 사용
    }

    @Test
    fun `가격 정책 조회 - 정책 없음 시 예외 발생`() {
        // when & then
        val exception = assertThrows(AiServiceException::class.java) {
            tokenPricingService.getPricingPolicy(
                vendor = Vendor.ANTHROPIC,
                model = "claude-3-5-sonnet",
                clientId = null
            )
        }

        assertEquals(ApiErrorCode.AI_REQUEST_VALIDATION_FAILED, exception.errorCode)
    }

    @Test
    fun `비용 계산 - 공통 정책 사용`() {
        // when
        val cost = tokenPricingService.calculateCost(
            vendor = Vendor.OPENAI,
            model = "gpt-4o-mini",
            clientId = null,
            contentType = ContentType.TEXT,
            inputTokens = 1000,
            outputTokens = 500
        )

        // then
        // 1000 input tokens = 100.0 / 1,000,000 * 1000 = 0.1 원
        // 500 output tokens = 300.0 / 1,000,000 * 500 = 0.15 원
        // 총 0.25 원
        val expectedCost = BigDecimal("0.25")
        assertEquals(0, cost.compareTo(expectedCost))
    }

    @Test
    fun `비용 계산 - Client별 정책 사용`() {
        // given
        val clientId = 1L
        val clientPolicy = TokenPricingPolicy.createForClient(
            clientId = clientId,
            vendor = Vendor.OPENAI,
            model = "gpt-4o-mini",
            inputTokenPricePerMillion = BigDecimal("50.0"),
            outputTokenPricePerMillion = BigDecimal("150.0")
        )
        tokenPricingPolicyRepository.save(clientPolicy)

        // when
        val cost = tokenPricingService.calculateCost(
            vendor = Vendor.OPENAI,
            model = "gpt-4o-mini",
            clientId = clientId,
            contentType = ContentType.TEXT,
            inputTokens = 1000,
            outputTokens = 500
        )

        // then
        // 1000 input tokens = 50.0 / 1,000,000 * 1000 = 0.05 원
        // 500 output tokens = 150.0 / 1,000,000 * 500 = 0.075 원
        // 총 0.125 원
        val expectedCost = BigDecimal("0.125")
        assertEquals(0, cost.compareTo(expectedCost))
    }
}
