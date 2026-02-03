package com.example.demo.adapter.`in`.scheduler

import com.example.demo.business.QuotaManagementService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 쿼터 주기별 리셋 스케줄러
 * 가격 기반 및 토큰 기반 쿼터를 주기적으로 리셋
 */
@Component
class QuotaResetScheduler(
    private val quotaManagementService: QuotaManagementService,
) {
    private val log = LoggerFactory.getLogger(QuotaResetScheduler::class.java)

    /**
     * 매 시간마다 리셋이 필요한 쿼터 확인 및 리셋
     */
    @Scheduled(cron = "0 0 * * * *") // 매 시간 정각
    fun resetQuotas() {
        log.info("쿼터 리셋 스케줄러 실행 시작")
        try {
            quotaManagementService.resetQuotas()
            log.info("쿼터 리셋 완료")
        } catch (e: Exception) {
            log.error("쿼터 리셋 중 오류 발생", e)
        }
    }
}
