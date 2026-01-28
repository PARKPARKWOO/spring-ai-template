package com.example.demo.common.config

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.springframework.context.ApplicationListener
import org.springframework.context.event.ContextClosedEvent
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

/**
 * 코루틴 설정 및 관리
 * 
 * - 애플리케이션 전역 코루틴 스코프 제공
 * - Graceful shutdown 시 코루틴 취소 처리
 */
@Component
class CoroutineConfig : ApplicationListener<ContextClosedEvent> {
    
    /**
     * 애플리케이션 전역 코루틴 스코프
     * SupervisorJob을 사용하여 한 코루틴의 실패가 다른 코루틴에 영향을 주지 않도록 함
     */
    val applicationScope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default
    )
    
    /**
     * I/O 작업용 코루틴 스코프
     * Dispatchers.IO는 기본적으로 64개 스레드 풀을 사용 (또는 CPU 코어 수 * 64)
     */
    val ioScope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO
    )
    
    /**
     * 애플리케이션 종료 시 코루틴 정리
     * Spring의 ContextClosedEvent를 받아서 모든 코루틴을 취소하고 완료될 때까지 대기
     */
    override fun onApplicationEvent(event: ContextClosedEvent) {
        val logger = org.slf4j.LoggerFactory.getLogger(CoroutineConfig::class.java)
        logger.info("애플리케이션 종료 시작 - 코루틴 취소 중...")
        
        // 모든 코루틴 취소
        val applicationJob = applicationScope.coroutineContext[Job]
        val ioJob = ioScope.coroutineContext[Job]
        
        applicationJob?.cancel()
        ioJob?.cancel()
        
        // 코루틴이 완료될 때까지 대기 (최대 30초)
        runBlocking {
            try {
                withTimeout(TimeUnit.SECONDS.toMillis(30)) {
                    // 코루틴이 모두 완료될 때까지 대기
                    applicationJob?.join()
                    ioJob?.join()
                }
            } catch (e: Exception) {
                logger.warn("코루틴 종료 대기 중 타임아웃 발생", e)
            }
        }
        
        logger.info("코루틴 정리 완료")
    }
}
