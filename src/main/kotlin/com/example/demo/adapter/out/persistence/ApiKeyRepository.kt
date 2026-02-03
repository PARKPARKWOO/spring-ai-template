package com.example.demo.adapter.out.persistence

import com.example.demo.model.Vendor
import com.example.demo.model.api.ApiKey
import org.springframework.data.jpa.repository.JpaRepository

interface ApiKeyRepository : JpaRepository<ApiKey, Long> {
    /** Application별 벤더 API 키 조회 (삭제되지 않은 것만) */
    fun findByApplicationIdAndVendorAndDeletedAtIsNull(
        applicationId: String,
        vendor: Vendor,
    ): ApiKey?

    /** Application별 API 키 목록 (삭제되지 않은 것만) */
    fun findByApplicationIdAndDeletedAtIsNull(applicationId: String): List<ApiKey>
}

