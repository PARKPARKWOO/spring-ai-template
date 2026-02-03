package com.example.demo.common.cache

import java.time.Duration
import java.util.concurrent.locks.Lock

/**
 * 캐시 인터페이스
 * Redis 등 외부 캐시 시스템과 로컬 메모리 캐시를 모두 지원하기 위한 추상화
 */
interface Cache {
    /**
     * 캐시에서 값을 조회
     * @param key 캐시 키
     * @return 캐시된 값 (없으면 null)
     */
    fun <T> get(key: String, type: Class<T>): T?

    /**
     * 캐시에 값을 저장
     * @param key 캐시 키
     * @param value 저장할 값
     * @param ttl TTL (Time To Live), null이면 만료 없음
     */
    fun set(key: String, value: Any?, ttl: Duration? = null)

    /**
     * 캐시에서 값을 삭제
     * @param key 캐시 키
     */
    fun delete(key: String)

    /**
     * 캐시 키에 대한 분산 락 획득
     * @param key 락 키
     * @param timeout 락 획득 대기 시간
     * @return 락 객체 (획득 실패 시 null)
     */
    fun tryLock(key: String, timeout: Duration = Duration.ofSeconds(5)): Lock?

    /**
     * 캐시 키 존재 여부 확인
     * @param key 캐시 키
     * @return 존재 여부
     */
    fun exists(key: String): Boolean

    /**
     * 캐시 전체 삭제
     */
    fun clear()
}
