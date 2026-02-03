package com.example.demo.common.cache

import org.slf4j.LoggerFactory
import jakarta.annotation.PreDestroy
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.Scheduled
import java.time.Duration

/**
 * 캐시 설정
 * Caffeine 기반 로컬 메모리 캐시를 Bean으로 등록
 */
@Configuration
class CacheConfig(
    private val applicationContext: ApplicationContext
) {
    private val log = LoggerFactory.getLogger(CacheConfig::class.java)

    @Bean
    fun cache(
        @Value("\${cache.max-size:10000}") maxSize: Long,
        @Value("\${cache.default-ttl-minutes:5}") defaultTtlMinutes: Long,
    ): Cache {
        return CaffeineCache(
            maxSize = maxSize,
            defaultTtl = Duration.ofMinutes(defaultTtlMinutes)
        )
    }

    /**
     * 캐시 인스턴스 가져오기 (순환 참조 방지)
     */
    private fun getCache(): Cache? {
        return try {
            applicationContext.getBean(Cache::class.java)
        } catch (e: Exception) {
            log.warn("Cache bean을 가져올 수 없습니다: ${e.message}")
            null
        }
    }

    /**
     * 캐시 통계 로깅 (10분마다 실행)
     * Caffeine은 자동으로 만료된 항목을 정리하므로 별도 정리 작업 불필요
     */
    @Scheduled(fixedRate = 600000) // 10분 = 600,000ms
    fun logCacheStats() {
        val cache = getCache() as? CaffeineCache ?: return
        val stats = cache.getStats()
        val hitRate = if (stats.hitCount + stats.missCount > 0) {
            (stats.hitCount.toDouble() / (stats.hitCount + stats.missCount) * 100).let { "%.2f".format(it) }
        } else {
            "0.00"
        }
        log.debug(
            "Cache stats - Entries: ${stats.totalEntries}, " +
                    "Hits: ${stats.hitCount}, Misses: ${stats.missCount}, " +
                    "Hit Rate: ${hitRate}%, Evictions: ${stats.evictionCount}, Locks: ${stats.lockCount}"
        )
    }

    /**
     * 애플리케이션 종료 시 캐시 정리
     */
    @PreDestroy
    fun cleanup() {
        val cache = getCache() as? CaffeineCache ?: return
        val stats = cache.getStats()
        log.info(
            "Cache cleanup - Total: ${stats.totalEntries}, " +
                    "Hits: ${stats.hitCount}, Misses: ${stats.missCount}, " +
                    "Evictions: ${stats.evictionCount}"
        )
        cache.clear()
    }
}
