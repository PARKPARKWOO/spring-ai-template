package com.example.demo.adapter.out.persistence

import com.example.demo.model.AiUsageLogs
import com.example.demo.model.QAiUsageLogs
import com.example.demo.model.Vendor
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.EntityManager
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

interface AiUsageLogsRepository: JpaRepository<AiUsageLogs, Long>, AiUsageLogsQueryRepository {
}

interface AiUsageLogsQueryRepository {
    fun sumTokensByClientIdAndVendor(clientId: Long, vendor: String, startDate: LocalDateTime?, endDate: LocalDateTime?): Long
}

@Repository
class AiUsageLogsQueryRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
): AiUsageLogsQueryRepository {
    private val qAiUsageLogs: QAiUsageLogs = QAiUsageLogs.aiUsageLogs

    fun findByClientIdAndVendor(
        clientId: Long,
        vendor: Vendor,
        startDate: LocalDateTime? = null,
        endDate: LocalDateTime? = null,
    ): List<AiUsageLogs> {
        return queryFactory
            .selectFrom(qAiUsageLogs)
            .where(
                qAiUsageLogs.clientId.eq(clientId),
                qAiUsageLogs.vendor.eq(vendor.name),
                startDateCondition(startDate),
                endDateCondition(endDate)
            )
            .orderBy(qAiUsageLogs.createdAt.desc())
            .fetch()
    }

    fun countByClientIdAndVendor(
        clientId: Long,
        vendor: Vendor,
        startDate: LocalDateTime? = null,
        endDate: LocalDateTime? = null,
    ): Long {
        return queryFactory
            .select(qAiUsageLogs.count())
            .from(qAiUsageLogs)
            .where(
                qAiUsageLogs.clientId.eq(clientId),
                qAiUsageLogs.vendor.eq(vendor.name),
                startDateCondition(startDate),
                endDateCondition(endDate)
            )
            .fetchOne() ?: 0L
    }

    private fun startDateCondition(startDate: LocalDateTime?): BooleanExpression? {
        return startDate?.let { qAiUsageLogs.createdAt.goe(it) }
    }

    private fun endDateCondition(endDate: LocalDateTime?): BooleanExpression? {
        return endDate?.let { qAiUsageLogs.createdAt.loe(it) }
    }

    override fun sumTokensByClientIdAndVendor(
        clientId: Long,
        vendor: String,
        startDate: LocalDateTime?,
        endDate: LocalDateTime?
    ): Long {
        val result = queryFactory
            .select(
                qAiUsageLogs.promptToken.add(qAiUsageLogs.completionToken).sum()
            )
            .from(qAiUsageLogs)
            .where(
                qAiUsageLogs.clientId.eq(clientId),
                qAiUsageLogs.vendor.eq(vendor),
                startDateCondition(startDate),
                endDateCondition(endDate)
            )
            .fetchOne()

        return result?.toLong() ?: 0L
    }
}

