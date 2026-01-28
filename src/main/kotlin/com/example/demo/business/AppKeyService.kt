package com.example.demo.business

import com.example.demo.adapter.out.persistence.AppKeyRepository
import com.example.demo.adapter.out.persistence.ClientRepository
import com.example.demo.business.exception.ClientServiceException
import com.example.demo.common.cache.Cache
import com.example.demo.common.logger
import com.example.demo.model.ApiErrorCode
import com.example.demo.model.AppKey
import com.example.demo.model.Client
import org.springframework.context.annotation.Lazy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

@Service
class AppKeyService(
    @field:Lazy private val appKeyRepository: AppKeyRepository,
    @field:Lazy private val clientRepository: ClientRepository,
    private val passwordEncoder: PasswordEncoder,
    @field:Lazy private val cache: Cache,
) {
    companion object {
        private const val APP_KEY_PREFIX = "appkey_"
        private const val KEY_LENGTH = 32 // 접두사 제외한 키 길이
        private val RANDOM = SecureRandom()
        
        // 거의 무제한 기간 (100년 후)
        private val ALMOST_UNLIMITED_EXPIRATION = LocalDateTime.now().plusYears(100)
        
        // 캐시 키 prefix
        private const val CACHE_KEY_PREFIX = "appkey:"
        private const val CACHE_ID_TO_KEY_PREFIX = "appkey:id:"
        
        // 캐시 TTL (5분)
        private val CACHE_TTL = Duration.ofMinutes(5)
    }

    /**
     * 새로운 AppKey 발급
     * @param clientId 클라이언트 ID
     * @param name 키 이름
     * @param description 키 설명
     * @return Pair<원본 키, AppKey 엔티티> - 원본 키는 한 번만 반환됨
     */
    @Transactional
    fun issueAppKey(
        clientId: Long,
        name: String,
        description: String? = null
    ): Pair<String, AppKey> {
        val client = clientRepository.findById(clientId)
            .orElseThrow { 
                ClientServiceException(
                    ApiErrorCode.CLIENT_NOT_FOUND,
                    "클라이언트 ID ${clientId}를 찾을 수 없습니다."
                )
            }

        // 원본 키 생성
        val originalKey = generateAppKey()
        
        // 키를 해시로 변환하여 저장
        val keyHash = passwordEncoder.encode(originalKey)
        
        val now = LocalDateTime.now()
        val appKey = AppKey(
            id = 0L,
            client = client,
            keyHash = keyHash,
            name = name,
            description = description,
            expiresAt = ALMOST_UNLIMITED_EXPIRATION, // 거의 무제한
            isActive = true,
            lastUsedAt = null,
            createdAt = now,
            updatedAt = now,
            deletedAt = null
        )
        
        val savedAppKey = appKeyRepository.save(appKey)
        
        // 캐시에 저장: 원본 키 -> AppKey 엔티티
        val cacheKey = "$CACHE_KEY_PREFIX$originalKey"
        cache.set(cacheKey, savedAppKey, CACHE_TTL)
        
        // 역방향 매핑 캐시: AppKey ID -> 원본 키 (캐시 무효화를 위해 필요)
        val idToKeyCacheKey = "$CACHE_ID_TO_KEY_PREFIX${savedAppKey.id}"
        cache.set(idToKeyCacheKey, originalKey, CACHE_TTL)
        
        logger().info("AppKey issued for clientId: $clientId, appKeyId: ${savedAppKey.id}")
        
        return Pair(originalKey, savedAppKey)
    }

    /**
     * AppKey 검증 (캐싱 적용)
     * @param appKey 원본 AppKey
     * @return 검증된 AppKey 엔티티 또는 null
     */
    @Transactional
    fun validateAppKey(appKey: String): AppKey? {
        val cacheKey = "$CACHE_KEY_PREFIX$appKey"
        
        // 캐시에서 먼저 조회
        val cachedResult = cache.get(cacheKey, AppKey::class.java)
        if (cachedResult != null) {
            // 캐시된 결과가 있으면 사용 가능 여부만 확인
            if (cachedResult.isUsable()) {
                // 마지막 사용 시간 업데이트 (비동기로 처리 가능하지만, 여기서는 동기 처리)
                updateLastUsedAt(cachedResult.id)
                return cachedResult
            } else {
                // 사용 불가능한 키는 캐시에서 제거
                cache.delete(cacheKey)
            }
        }

        // 캐시에 없으면 DB에서 조회
        val allActiveKeys = appKeyRepository.findAllActiveWithClient()

        for (key in allActiveKeys) {
            if (passwordEncoder.matches(appKey, key.keyHash)) {
                // 키가 사용 가능한지 확인
                if (!key.isUsable()) {
                    logger().warn("AppKey is not usable: ${key.id}")
                    // 사용 불가능한 키는 캐시에 null로 저장 (짧은 TTL)
                    cache.set(cacheKey, null, Duration.ofMinutes(1))
                    return null
                }

                // 마지막 사용 시간 업데이트
                val updatedKey = AppKey(
                    id = key.id,
                    client = key.client, // fetch join으로 이미 로드됨
                    keyHash = key.keyHash,
                    name = key.name,
                    description = key.description,
                    expiresAt = key.expiresAt,
                    isActive = key.isActive,
                    lastUsedAt = LocalDateTime.now(), // 마지막 사용 시간 업데이트
                    createdAt = key.createdAt,
                    updatedAt = LocalDateTime.now(),
                    deletedAt = key.deletedAt
                )
                appKeyRepository.save(updatedKey)

                // 캐시에 저장 (Client 엔티티도 포함되어 있으므로 함께 캐싱)
                cache.set(cacheKey, updatedKey, CACHE_TTL)

                // 역방향 매핑 캐시도 생성/업데이트 (캐시 무효화를 위해 필요)
                // 원본 키 값을 알고 있으므로 역방향 매핑을 생성할 수 있음
                val idToKeyCacheKey = "$CACHE_ID_TO_KEY_PREFIX${updatedKey.id}"
                cache.set(idToKeyCacheKey, appKey, CACHE_TTL)

                return updatedKey
            }
        }
        
        // 일치하는 키가 없으면 캐시에 null로 저장 (짧은 TTL로 무효한 키 재시도 방지)
        cache.set(cacheKey, null, Duration.ofMinutes(1))
        return null
    }
    
    /**
     * 마지막 사용 시간 업데이트 (캐시와 별개로 처리)
     */
    @Transactional
     fun updateLastUsedAt(appKeyId: Long) {
        val appKey = appKeyRepository.findById(appKeyId).orElse(null) ?: return
        
        val updatedKey = AppKey(
            id = appKey.id,
            client = appKey.client,
            keyHash = appKey.keyHash,
            name = appKey.name,
            description = appKey.description,
            expiresAt = appKey.expiresAt,
            isActive = appKey.isActive,
            lastUsedAt = LocalDateTime.now(),
            createdAt = appKey.createdAt,
            updatedAt = LocalDateTime.now(),
            deletedAt = appKey.deletedAt
        )
        appKeyRepository.save(updatedKey)
    }

    /**
     * AppKey 검증 (최적화된 버전 - 해시로 직접 조회)
     * 주의: 이 방법은 해시가 정확히 일치하는 경우에만 작동합니다.
     * BCrypt는 매번 다른 해시를 생성하므로, 모든 키를 확인해야 합니다.
     */
    @Transactional
    fun validateAppKeyOptimized(appKey: String): AppKey? {
        // BCrypt는 매번 다른 salt를 사용하므로 해시로 직접 조회할 수 없음
        // 따라서 모든 활성 키를 확인해야 함
        // 성능 최적화를 위해서는 별도의 키-해시 매핑 테이블이나 다른 방식 고려 필요
        return validateAppKey(appKey)
    }

    /**
     * AppKey 비활성화
     */
    @Transactional
    fun deactivateAppKey(appKeyId: Long, clientId: Long): Boolean {
        val appKey = appKeyRepository.findByIdAndClientId(appKeyId, clientId)
            ?: return false
        
        val updatedKey = AppKey(
            id = appKey.id,
            client = appKey.client,
            keyHash = appKey.keyHash,
            name = appKey.name,
            description = appKey.description,
            expiresAt = appKey.expiresAt,
            isActive = false,
            lastUsedAt = appKey.lastUsedAt,
            createdAt = appKey.createdAt,
            updatedAt = LocalDateTime.now(),
            deletedAt = appKey.deletedAt
        )
        
        appKeyRepository.save(updatedKey)
        
        // 캐시 무효화 (모든 AppKey에 대해 캐시를 무효화해야 하므로, 
        // 특정 키를 찾기 위해 모든 활성 키를 확인해야 함)
        invalidateAppKeyCache(appKeyId)
        
        logger().info("AppKey deactivated: $appKeyId")
        return true
    }

    /**
     * AppKey 삭제 (소프트 삭제)
     */
    @Transactional
    fun deleteAppKey(appKeyId: Long, clientId: Long): Boolean {
        val appKey = appKeyRepository.findByIdAndClientId(appKeyId, clientId)
            ?: return false
        
        val updatedKey = AppKey(
            id = appKey.id,
            client = appKey.client,
            keyHash = appKey.keyHash,
            name = appKey.name,
            description = appKey.description,
            expiresAt = appKey.expiresAt,
            isActive = appKey.isActive,
            lastUsedAt = appKey.lastUsedAt,
            createdAt = appKey.createdAt,
            updatedAt = LocalDateTime.now(),
            deletedAt = LocalDateTime.now()
        )
        
        appKeyRepository.save(updatedKey)
        
        // 캐시 무효화
        invalidateAppKeyCache(appKeyId)
        
        logger().info("AppKey deleted: $appKeyId")
        return true
    }
    
    /**
     * AppKey 캐시 무효화
     * AppKey ID를 통해 원본 키를 찾아서 캐시에서 제거합니다.
     */
    private fun invalidateAppKeyCache(appKeyId: Long) {
        try {
            // 역방향 매핑 캐시에서 원본 키 조회
            val idToKeyCacheKey = "$CACHE_ID_TO_KEY_PREFIX$appKeyId"
            val originalKey = cache.get(idToKeyCacheKey, String::class.java)
            
            if (originalKey != null) {
                // 원본 키로 AppKey 엔티티 캐시 제거
                val cacheKey = "$CACHE_KEY_PREFIX$originalKey"
                cache.delete(cacheKey)
                logger().debug("AppKey cache invalidated for appKeyId: $appKeyId, originalKey: ${originalKey.take(20)}...")
            } else {
                // 역방향 매핑이 캐시에 없으면 DB에서 조회 시도
                // (캐시가 만료되었거나 발급 시 캐시에 저장되지 않은 경우)
                logger().debug("AppKey ID to key mapping not found in cache for appKeyId: $appKeyId, will rely on TTL")
            }
            
            // 역방향 매핑 캐시도 제거
            cache.delete(idToKeyCacheKey)
        } catch (e: Exception) {
            logger().warn("Failed to invalidate AppKey cache for appKeyId: $appKeyId", e)
        }
    }

    /**
     * 클라이언트의 모든 AppKey 조회
     */
    fun getAppKeysByClientId(clientId: Long): List<AppKey> {
        return appKeyRepository.findByClientId(clientId)
    }

    /**
     * AppKey 생성 (랜덤 문자열)
     */
    private fun generateAppKey(): String {
        val bytes = ByteArray(KEY_LENGTH)
        RANDOM.nextBytes(bytes)
        val key = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        return "$APP_KEY_PREFIX$key"
    }
}
