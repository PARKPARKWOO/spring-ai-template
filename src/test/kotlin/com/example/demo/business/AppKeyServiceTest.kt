package com.example.demo.business

import com.example.demo.adapter.out.persistence.AppKeyRepository
import com.example.demo.adapter.out.persistence.ClientRepository
import com.example.demo.business.exception.ClientServiceException
import com.example.demo.common.cache.Cache
import com.example.demo.model.ApiErrorCode
import com.example.demo.model.AppKey
import com.example.demo.model.Client
import com.example.demo.model.ClientRole
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AppKeyServiceTest {

    @Autowired
    private lateinit var appKeyService: AppKeyService

    @Autowired
    private lateinit var clientRepository: ClientRepository

    @Autowired
    private lateinit var appKeyRepository: AppKeyRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var cache: Cache

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
    }

    @Test
    fun `AppKey 발급 - 성공`() {
        // when
        val (originalKey, appKey) = appKeyService.issueAppKey(
            clientId = testClient.id,
            name = "Test Key",
            description = "Test Description"
        )

        // then
        assertNotNull(originalKey)
        assertTrue(originalKey.startsWith("appkey_"))
        assertEquals(testClient.id, appKey.client.id)
        assertEquals("Test Key", appKey.name)
        assertEquals("Test Description", appKey.description)
        assertTrue(appKey.isActive)
        assertNull(appKey.deletedAt)
        
        // 해시 검증
        assertTrue(passwordEncoder.matches(originalKey, appKey.keyHash))
    }

    @Test
    fun `AppKey 발급 - 존재하지 않는 클라이언트`() {
        // when & then
        val exception = assertThrows(ClientServiceException::class.java) {
            appKeyService.issueAppKey(
                clientId = 99999L,
                name = "Test Key"
            )
        }
        
        assertEquals(ApiErrorCode.CLIENT_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun `AppKey 검증 - 성공`() {
        // given
        val (originalKey, savedAppKey) = appKeyService.issueAppKey(
            clientId = testClient.id,
            name = "Test Key"
        )

        // when
        val validatedAppKey = appKeyService.validateAppKey(originalKey)

        // then
        assertNotNull(validatedAppKey)
        assertEquals(savedAppKey.id, validatedAppKey!!.id)
        assertEquals(testClient.id, validatedAppKey.client.id)
        assertTrue(validatedAppKey.isUsable())
    }

    @Test
    fun `AppKey 검증 - 유효하지 않은 키`() {
        // when
        val validatedAppKey = appKeyService.validateAppKey("invalid_key")

        // then
        assertNull(validatedAppKey)
    }

    @Test
    fun `AppKey 검증 - 캐시 활용`() {
        // given
        val (originalKey, _) = appKeyService.issueAppKey(
            clientId = testClient.id,
            name = "Test Key"
        )

        // when - 첫 번째 검증 (DB에서 조회)
        val firstValidation = appKeyService.validateAppKey(originalKey)
        
        // when - 두 번째 검증 (캐시에서 조회)
        val secondValidation = appKeyService.validateAppKey(originalKey)

        // then
        assertNotNull(firstValidation)
        assertNotNull(secondValidation)
        assertEquals(firstValidation!!.id, secondValidation!!.id)
    }

    @Test
    fun `AppKey 비활성화 - 성공`() {
        // given
        val (originalKey, savedAppKey) = appKeyService.issueAppKey(
            clientId = testClient.id,
            name = "Test Key"
        )

        // when
        val result = appKeyService.deactivateAppKey(savedAppKey.id, testClient.id)

        // then
        assertTrue(result)
        val deactivatedAppKey = appKeyRepository.findById(savedAppKey.id).orElse(null)
        assertNotNull(deactivatedAppKey)
        assertFalse(deactivatedAppKey!!.isActive)
        
        // 비활성화된 키는 검증 실패
        val validatedAppKey = appKeyService.validateAppKey(originalKey)
        assertNull(validatedAppKey)
    }

    @Test
    fun `AppKey 삭제 - 성공`() {
        // given
        val (originalKey, savedAppKey) = appKeyService.issueAppKey(
            clientId = testClient.id,
            name = "Test Key"
        )

        // when
        val result = appKeyService.deleteAppKey(savedAppKey.id, testClient.id)

        // then
        assertTrue(result)
        val deletedAppKey = appKeyRepository.findById(savedAppKey.id).orElse(null)
        assertNotNull(deletedAppKey)
        assertNotNull(deletedAppKey!!.deletedAt)
        
        // 삭제된 키는 검증 실패
        val validatedAppKey = appKeyService.validateAppKey(originalKey)
        assertNull(validatedAppKey)
    }

    @Test
    fun `AppKey 삭제 - 다른 클라이언트의 키 삭제 시도`() {
        // given
        val otherClient = Client(
            id = 0L,
            name = "OtherClient",
            password = passwordEncoder.encode("password"),
            description = "Other Client",
            role = ClientRole.CLIENT,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
            deletedAt = null
        )
        val savedOtherClient = clientRepository.save(otherClient)
        
        val (_, savedAppKey) = appKeyService.issueAppKey(
            clientId = testClient.id,
            name = "Test Key"
        )

        // when
        val result = appKeyService.deleteAppKey(savedAppKey.id, savedOtherClient.id)

        // then
        assertFalse(result)
    }
}
