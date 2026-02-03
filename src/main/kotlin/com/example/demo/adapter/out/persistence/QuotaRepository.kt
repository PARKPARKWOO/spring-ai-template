package com.example.demo.adapter.out.persistence

import com.example.demo.model.Quota
import com.example.demo.model.Vendor
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock

interface QuotaRepository: JpaRepository<Quota, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findByClientIdAndVendor(clientId: Long, vendor: Vendor): Quota?
}