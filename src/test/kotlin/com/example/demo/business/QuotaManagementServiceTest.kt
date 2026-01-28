package com.example.demo.business

import com.example.demo.adapter.out.persistence.ClientPricingQuotaRepository
import com.example.demo.adapter.out.persistence.ClientRepository
import com.example.demo.adapter.out.persistence.ClientTokenQuotaRepository
import com.example.demo.adapter.out.persistence.TokenPricingPolicyRepository
import com.example.demo.business.exception.AiServiceException
import com.example.demo.common.constants.CycleUnit
import com.example.demo.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class QuotaManagementServiceTest {

    @Autowired
    private lateinit var quotaManagementService: QuotaManagementService

    @Autowired
    private lateinit var clientRepository: ClientRepository

    @Autowired
    private lateinit var clientPricingQuotaRepository: ClientPricingQuotaRepository

    @Autowired
    private lateinit var clientTokenQuotaRepository: ClientTokenQuotaRepository

    @Autowired
    private lateinit var tokenPricingPolicyRepository: TokenPricingPolicyRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    private lateinit var testClient: Client

    @BeforeEach
    fun setUp() {
        // 테스트용 클라이언트 생성
        testClient = Client(
            id = 0L,
            name = "TestClient",
            password = passwordEncoder.encode("password"),
            description = "Test Client",
            role = ClientRole.CLIENT,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
            deletedAt = null
        )
        testClient = clientRepository.save(testClient)

        // 테스트용 가격 정책 생성
        val pricingPolicy = TokenPricingPolicy(
            id = 0L,
            vendor = Vendor.OPENAI,
            model = "gpt-4o-mini",
            clientId = null, // 공통 정책
            inputTokenPricePerMillion = BigDecimal("100.0"),
            outputTokenPricePerMillion = BigDecimal("300.0"),
            isActive = true,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
            deletedAt = null
        )
        tokenPricingPolicyRepository.save(pricingPolicy)
    }

    @Test
    fun `쿼터 정책 조회 - PRICING_ONLY`() {
        // given
        val pricingQuota = ClientPricingQuota.create(
            clientId = testClient.id,
            maxAmount = BigDecimal("10000.0"),
            cycleUnit = CycleUnit.MONTHS
        )
        clientPricingQuotaRepository.save(pricingQuota)

        // when
        val policy = quotaManagementService.getQuotaPolicy(
            clientId = testClient.id,
            vendor = Vendor.OPENAI,
            model = "gpt-4o-mini"
        )

        // then
        assertEquals(QuotaPolicy.PRICING_ONLY, policy)
    }

    @Test
    fun `쿼터 정책 조회 - TOKEN_ONLY`() {
        // given
        val tokenQuota = ClientTokenQuota.create(
            clientId = testClient.id,
            maxTokens = 1000000L,
            cycleUnit = CycleUnit.DAYS,
            vendor = Vendor.OPENAI,
            model = "gpt-4o-mini"
        )
        clientTokenQuotaRepository.save(tokenQuota)

        // when
        val policy = quotaManagementService.getQuotaPolicy(
            clientId = testClient.id,
            vendor = Vendor.OPENAI,
            model = "gpt-4o-mini"
        )

        // then
        assertEquals(QuotaPolicy.TOKEN_ONLY, policy)
    }

    @Test
    fun `쿼터 정책 조회 - BOTH`() {
        // given
        val pricingQuota = ClientPricingQuota.create(
            clientId = testClient.id,
            maxAmount = BigDecimal("10000.0"),
            cycleUnit = CycleUnit.MONTHS
        )
        clientPricingQuotaRepository.save(pricingQuota)

        val tokenQuota = ClientTokenQuota.create(
            clientId = testClient.id,
            maxTokens = 1000000L,
            cycleUnit = CycleUnit.DAYS,
            vendor = Vendor.OPENAI,
            model = "gpt-4o-mini"
        )
        clientTokenQuotaRepository.save(tokenQuota)

        // when
        val policy = quotaManagementService.getQuotaPolicy(
            clientId = testClient.id,
            vendor = Vendor.OPENAI,
            model = "gpt-4o-mini"
        )

        // then
        assertEquals(QuotaPolicy.BOTH, policy)
    }

    @Test
    fun `쿼터 정책 조회 - NONE`() {
        // when
        val policy = quotaManagementService.getQuotaPolicy(
            clientId = testClient.id,
            vendor = Vendor.OPENAI,
            model = "gpt-4o-mini"
        )

        // then
        assertEquals(QuotaPolicy.NONE, policy)
    }

    @Test
    fun `가격 기반 쿼터 할당 - 성공`() {
        // given
        val pricingQuota = ClientPricingQuota.create(
            clientId = testClient.id,
            maxAmount = BigDecimal("10000.0"),
            cycleUnit = CycleUnit.MONTHS
        )
        clientPricingQuotaRepository.save(pricingQuota)

        // when
        quotaManagementService.allocatePricingQuota(
            clientId = testClient.id,
            amount = BigDecimal("1000.0")
        )

        // then
        val updatedQuota = clientPricingQuotaRepository.findByClientIdWithLock(testClient.id).first()
        assertEquals(BigDecimal("1000.0"), updatedQuota.getCurrentAmount())
    }

    @Test
    fun `가격 기반 쿼터 할당 - 초과 시 예외 발생`() {
        // given
        val pricingQuota = ClientPricingQuota.create(
            clientId = testClient.id,
            maxAmount = BigDecimal("1000.0"),
            cycleUnit = CycleUnit.MONTHS
        )
        clientPricingQuotaRepository.save(pricingQuota)

        // when & then
        val exception = assertThrows(AiServiceException::class.java) {
            quotaManagementService.allocatePricingQuota(
                clientId = testClient.id,
                amount = BigDecimal("2000.0")
            )
        }

        assertEquals(ApiErrorCode.AI_QUOTA_EXCEEDED, exception.errorCode)
    }

    @Test
    fun `토큰 기반 쿼터 할당 - 성공`() {
        // given
        val tokenQuota = ClientTokenQuota.create(
            clientId = testClient.id,
            maxTokens = 1000000L,
            cycleUnit = CycleUnit.DAYS,
            vendor = Vendor.OPENAI,
            model = "gpt-4o-mini"
        )
        clientTokenQuotaRepository.save(tokenQuota)

        // when
        quotaManagementService.allocateTokenQuota(
            clientId = testClient.id,
            vendor = Vendor.OPENAI,
            model = "gpt-4o-mini",
            contentType = ContentType.TEXT,
            tokens = 10000L
        )

        // then
        val updatedQuota = clientTokenQuotaRepository.findByClientIdAndVendorAndModelWithLock(
            testClient.id, Vendor.OPENAI, "gpt-4o-mini"
        ).first()
        assertEquals(10000L, updatedQuota.getCurrentTokens())
    }

    @Test
    fun `토큰 기반 쿼터 할당 - 초과 시 예외 발생`() {
        // given
        val tokenQuota = ClientTokenQuota.create(
            clientId = testClient.id,
            maxTokens = 1000L,
            cycleUnit = CycleUnit.DAYS,
            vendor = Vendor.OPENAI,
            model = "gpt-4o-mini"
        )
        clientTokenQuotaRepository.save(tokenQuota)

        // when & then
        val exception = assertThrows(AiServiceException::class.java) {
            quotaManagementService.allocateTokenQuota(
                clientId = testClient.id,
                vendor = Vendor.OPENAI,
                model = "gpt-4o-mini",
                contentType = ContentType.TEXT,
                tokens = 2000L
            )
        }

        assertEquals(ApiErrorCode.AI_QUOTA_EXCEEDED, exception.errorCode)
    }

    @Test
    fun `통합 쿼터 할당 - PRICING_ONLY 정책`() {
        // given
        val pricingQuota = ClientPricingQuota.create(
            clientId = testClient.id,
            maxAmount = BigDecimal("10000.0"),
            cycleUnit = CycleUnit.MONTHS
        )
        clientPricingQuotaRepository.save(pricingQuota)

        // when
        quotaManagementService.allocateQuota(
            clientId = testClient.id,
            vendor = Vendor.OPENAI,
            model = "gpt-4o-mini",
            inputTokens = 1000,
            outputTokens = 500,
            contentType = ContentType.TEXT
        )

        // then
        val updatedQuota = clientPricingQuotaRepository.findByClientIdWithLock(testClient.id).first()
        assertTrue(updatedQuota.getCurrentAmount() > BigDecimal.ZERO)
    }

    @Test
    fun `통합 쿼터 할당 - TOKEN_ONLY 정책`() {
        // given
        val tokenQuota = ClientTokenQuota.create(
            clientId = testClient.id,
            maxTokens = 1000000L,
            cycleUnit = CycleUnit.DAYS,
            vendor = Vendor.OPENAI,
            model = "gpt-4o-mini"
        )
        clientTokenQuotaRepository.save(tokenQuota)

        // when
        quotaManagementService.allocateQuota(
            clientId = testClient.id,
            vendor = Vendor.OPENAI,
            model = "gpt-4o-mini",
            inputTokens = 1000,
            outputTokens = 500,
            contentType = ContentType.TEXT
        )

        // then
        val updatedQuota = clientTokenQuotaRepository.findByClientIdAndVendorAndModelWithLock(
            testClient.id, Vendor.OPENAI, "gpt-4o-mini"
        ).first()
        assertEquals(1500L, updatedQuota.getCurrentTokens())
    }

    @Test
    fun `통합 쿼터 할당 - BOTH 정책`() {
        // given
        val pricingQuota = ClientPricingQuota.create(
            clientId = testClient.id,
            maxAmount = BigDecimal("10000.0"),
            cycleUnit = CycleUnit.MONTHS
        )
        clientPricingQuotaRepository.save(pricingQuota)

        val tokenQuota = ClientTokenQuota.create(
            clientId = testClient.id,
            maxTokens = 1000000L,
            cycleUnit = CycleUnit.DAYS,
            vendor = Vendor.OPENAI,
            model = "gpt-4o-mini"
        )
        clientTokenQuotaRepository.save(tokenQuota)

        // when
        quotaManagementService.allocateQuota(
            clientId = testClient.id,
            vendor = Vendor.OPENAI,
            model = "gpt-4o-mini",
            inputTokens = 1000,
            outputTokens = 500,
            contentType = ContentType.TEXT
        )

        // then
        val updatedPricingQuota = clientPricingQuotaRepository.findByClientIdWithLock(testClient.id).first()
        val updatedTokenQuota = clientTokenQuotaRepository.findByClientIdAndVendorAndModelWithLock(
            testClient.id, Vendor.OPENAI, "gpt-4o-mini"
        ).first()
        
        assertTrue(updatedPricingQuota.getCurrentAmount() > BigDecimal.ZERO)
        assertEquals(1500L, updatedTokenQuota.getCurrentTokens())
    }

    @Test
    fun `실제 사용량으로 조정 - PRICING_ONLY 정책`() {
        // given
        val pricingQuota = ClientPricingQuota.create(
            clientId = testClient.id,
            maxAmount = BigDecimal("10000.0"),
            cycleUnit = CycleUnit.MONTHS
        )
        clientPricingQuotaRepository.save(pricingQuota)

        // 예약된 비용 할당
        quotaManagementService.allocateQuota(
            clientId = testClient.id,
            vendor = Vendor.OPENAI,
            model = "gpt-4o-mini",
            inputTokens = 1000,
            outputTokens = 500,
            contentType = ContentType.TEXT
        )

        val beforeAdjust = clientPricingQuotaRepository.findByClientIdWithLock(testClient.id).first()
        val allocatedAmount = beforeAdjust.getCurrentAmount()

        // when - 실제 사용량이 예약보다 적은 경우
        quotaManagementService.adjustByActualUsage(
            clientId = testClient.id,
            vendor = Vendor.OPENAI,
            model = "gpt-4o-mini",
            contentType = ContentType.TEXT,
            allocatedInputTokens = 1000,
            allocatedOutputTokens = 500,
            actualInputTokens = 800,
            actualOutputTokens = 400
        )

        // then
        val afterAdjust = clientPricingQuotaRepository.findByClientIdWithLock(testClient.id).first()
        assertTrue(afterAdjust.getCurrentAmount() < allocatedAmount) // 실제 사용량이 적으므로 감소
    }

    @Test
    fun `실제 사용량으로 조정 - TOKEN_ONLY 정책`() {
        // given
        val tokenQuota = ClientTokenQuota.create(
            clientId = testClient.id,
            maxTokens = 1000000L,
            cycleUnit = CycleUnit.DAYS,
            vendor = Vendor.OPENAI,
            model = "gpt-4o-mini"
        )
        clientTokenQuotaRepository.save(tokenQuota)

        // 예약된 토큰 할당
        quotaManagementService.allocateQuota(
            clientId = testClient.id,
            vendor = Vendor.OPENAI,
            model = "gpt-4o-mini",
            inputTokens = 1000,
            outputTokens = 500,
            contentType = ContentType.TEXT
        )

        val beforeAdjust = clientTokenQuotaRepository.findByClientIdAndVendorAndModelWithLock(
            testClient.id, Vendor.OPENAI, "gpt-4o-mini"
        ).first()
        val allocatedTokens = beforeAdjust.getCurrentTokens()

        // when - 실제 사용량이 예약보다 많은 경우
        quotaManagementService.adjustByActualUsage(
            clientId = testClient.id,
            vendor = Vendor.OPENAI,
            model = "gpt-4o-mini",
            contentType = ContentType.TEXT,
            allocatedInputTokens = 1000,
            allocatedOutputTokens = 500,
            actualInputTokens = 1200,
            actualOutputTokens = 600
        )

        // then
        val afterAdjust = clientTokenQuotaRepository.findByClientIdAndVendorAndModelWithLock(
            testClient.id, Vendor.OPENAI, "gpt-4o-mini"
        ).first()
        assertTrue(afterAdjust.getCurrentTokens() > allocatedTokens) // 실제 사용량이 많으므로 증가
    }
}
