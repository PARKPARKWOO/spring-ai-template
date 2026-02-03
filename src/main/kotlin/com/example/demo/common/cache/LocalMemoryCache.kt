package com.example.demo.common.cache

import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * 로컬 메모리 기반 캐시 구현체
 * Redis 없이 애플리케이션 메모리에서만 동작
 */
class LocalMemoryCache : Cache {
    private val log = LoggerFactory.getLogger(LocalMemoryCache::class.java)
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val locks = ConcurrentHashMap<String, ReentrantLock>()
    private val globalLock = ReentrantReadWriteLock()

    /**
     * 캐시 엔트리
     * 값과 만료 시간을 함께 저장
     */
    private data class CacheEntry(
        val value: Any,
        val expiresAt: Instant?
    ) {
        fun isExpired(): Boolean {
            return expiresAt != null && Instant.now().isAfter(expiresAt)
        }
    }

    override fun <T> get(key: String, type: Class<T>): T? {
        return globalLock.read {
            val entry = cache[key] ?: return null

            // 만료된 항목 제거
            if (entry.isExpired()) {
                cache.remove(key)
                return null
            }

            try {
                type.cast(entry.value)
            } catch (e: ClassCastException) {
                log.warn("Cache type mismatch for key: $key, expected: ${type.name}, got: ${entry.value.javaClass.name}")
                null
            }
        }
    }

    override fun set(key: String, value: Any?, ttl: Duration?) {
        if (value == null) {
            delete(key)
            return
        }

        globalLock.write {
            val expiresAt = ttl?.let { Instant.now().plus(it) }
            cache[key] = CacheEntry(value, expiresAt)
        }
    }

    override fun delete(key: String) {
        globalLock.write {
            cache.remove(key)
        }
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
        return globalLock.read {
            val entry = cache[key] ?: return false
            if (entry.isExpired()) {
                cache.remove(key)
                return false
            }
            true
        }
    }

    override fun clear() {
        globalLock.write {
            cache.clear()
            locks.clear()
        }
    }

    /**
     * 만료된 항목 정리 (주기적으로 호출)
     */
    fun evictExpired() {
        globalLock.write {
            val now = Instant.now()
            val expiredKeys = cache.entries
                .filter { it.value.expiresAt != null && now.isAfter(it.value.expiresAt) }
                .map { it.key }
                .toList()

            expiredKeys.forEach { cache.remove(it) }

            if (expiredKeys.isNotEmpty()) {
                log.debug("Evicted ${expiredKeys.size} expired cache entries")
            }
        }
    }

    /**
     * 캐시 통계 정보
     */
    fun getStats(): CacheStats {
        return globalLock.read {
            val now = Instant.now()
            val total = cache.size
            val expired = cache.values.count { it.expiresAt != null && now.isAfter(it.expiresAt) }
            val active = total - expired

            CacheStats(
                totalEntries = total,
                activeEntries = active,
                expiredEntries = expired,
                lockCount = locks.size
            )
        }
    }

    data class CacheStats(
        val totalEntries: Int,
        val activeEntries: Int,
        val expiredEntries: Int,
        val lockCount: Int
    )
}
