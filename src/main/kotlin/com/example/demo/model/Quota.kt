package com.example.demo.model

import com.example.demo.business.exception.BusinessException
import com.example.demo.common.constants.CycleUnit
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

@Entity
class Quota (
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private val id: Long,
    private val clientId: Long,
    @Enumerated(EnumType.STRING)
    private val vendor: Vendor,
    private val capacity: Long,
    private var reservationAmount: Long,
    private var serviceStartedAt: LocalDateTime,
    private var nextRefreshAt: LocalDateTime,
    @Enumerated(EnumType.STRING)
    private var cycleUnit: CycleUnit,
){
    companion object {
        private const val DEFAULT_AMOUNT = 10000

        fun create(clientId: Long, vendor: Vendor, capacity: Long, cycleUnit: CycleUnit): Quota {
            val now = LocalDateTime.now()
            val chronoUnit = cycleUnit.toChronoUnit()
            return Quota(
                id = 0L,
                clientId = clientId,
                vendor = vendor,
                reservationAmount = 0,
                serviceStartedAt = now,
                capacity = capacity,
                nextRefreshAt = now.plus(1, chronoUnit),
                cycleUnit = cycleUnit,
            )
        }
    }
    fun reservation(inputToken: Int): Long {
        val amount = capacity - (reservationAmount + inputToken)

        val isOver = reservationAmount >= capacity || amount <= 0
        if (isOver) {
            throw BusinessException(ErrorCode.EXCEEDED_QUOTA)
        }

        val allocate = if (amount >= DEFAULT_AMOUNT) {
            amount - DEFAULT_AMOUNT
        } else {
            amount
        }
        
        reservationAmount += allocate
        return DEFAULT_AMOUNT.toLong()
    }

    fun refresh() {
        reservationAmount = 0
    }

    fun decreaseReservationByUsage(reservationAmount: Int, usage: Int) {
        if (reservationAmount >= usage) {
            this.reservationAmount -= ( reservationAmount - usage)
        } else {
            this.reservationAmount += (usage - reservationAmount)
        }
    }
}