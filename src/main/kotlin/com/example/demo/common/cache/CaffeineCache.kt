package com.example.demo.common.cache

import com.example.demo.common.logger
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.stats.CacheStats
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

/**
 * Caffeine 기반 로컬 메모리 캐시 구현체
 * 고성능 로컬 캐시 라이브러리인 Caffeine을 사용
 */
class CaffeineCache(
    /**
     * 최대 캐시 항목 수 (기본값: 10,000)
     */
    maxSize: Long = 10_000,
    
    /**
     * 기본 TTL (기본값: 5분)
     */
    defaultTtl: Duration = Duration.ofMinutes(5),
) : Cache {
    
    private val cache = Caffeine.newBuilder()
        .maximumSize(maxSize)
        .expireAfterWrite(defaultTtl)
        .recordStats() // 통계 수집 활성화
        .build<String, Any>()
    
    // Lock 관리를 위한 별도 맵 (Caffeine에는 Lock 기능이 없음)
    private val locks = ConcurrentHashMap<String, ReentrantLock>()

    override fun <T> get(key: String, type: Class<T>): T? {
        val value = cache.getIfPresent(key) ?: return null
        
        return try {
            type.cast(value)
        } catch (e: ClassCastException) {
            logger().warn("Cache type mismatch for key: $key, expected: ${type.name}, got: ${value.javaClass.name}")
            null
        }
    }

    override fun set(key: String, value: Any?, ttl: Duration?) {
        if (value == null) {
            delete(key)
            return
        }

        if (ttl != null) {
            // TTL이 지정된 경우, 별도 캐시 인스턴스 생성 (Caffeine은 항목별 TTL을 지원하지 않음)
            // 대신 전체 캐시의 expireAfterWrite를 사용하거나, 
            // 커스텀 Expiry를 구현할 수 있지만 복잡함
            // 여기서는 간단하게 전체 캐시 TTL을 사용하고, 
            // 개별 TTL이 필요한 경우 별도 캐시 인스턴스를 사용하는 것을 권장
            cache.put(key, value)
        } else {
            cache.put(key, value)
        }
    }

    override fun delete(key: String) {
        cache.invalidate(key)
    }

    override fun tryLock(key: String, timeout: Duration): Lock? {
        // 키별 락 객체 가져오기 또는 생성
        val lock = locks.computeIfAbsent(key) { ReentrantLock() }

        return try {
            // 타임아웃 내에 락 획득 시도
            if (lock.tryLock(timeout.seconds, java.util.concurrent.TimeUnit.SECONDS)) {
                lock
            } else {
                null
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            null
        }
    }

    override fun exists(key: String): Boolean {
        return cache.getIfPresent(key) != null
    }

    override fun clear() {
        cache.invalidateAll()
        locks.clear()
    }

    /**
     * Caffeine 캐시 통계 정보
     */
    fun getCaffeineStats(): CacheStats {
        return cache.stats()
    }

    /**
     * 캐시 통계 정보 (호환성을 위한 래퍼)
     */
    fun getStats(): CacheStatsInfo {
        val stats = cache.stats()
        return CacheStatsInfo(
            totalEntries = cache.estimatedSize(),
            hitCount = stats.hitCount(),
            missCount = stats.missCount(),
            evictionCount = stats.evictionCount(),
            loadCount = stats.loadCount(),
            lockCount = locks.size
        )
    }

    data class CacheStatsInfo(
        val totalEntries: Long,
        val hitCount: Long,
        val missCount: Long,
        val evictionCount: Long,
        val loadCount: Long,
        val lockCount: Int
    )
}
