package com.example.demo.adapter.out.persistence

import com.example.demo.model.ApiKeyTier
import com.example.demo.model.Vendor
import com.example.demo.model.api.ApiKey
import org.springframework.data.jpa.repository.JpaRepository

interface ApiKeyRepository : JpaRepository<ApiKey, Long> {
    /** Application별 벤더 API 키 목록 조회 (삭제되지 않은 것만) */
    fun findByApplicationIdAndVendorAndDeletedAtIsNull(
        applicationId: String,
        vendor: Vendor,
    ): List<ApiKey>

    /** Application별 벤더 + 티어 API 키 목록 조회 */
    fun findByApplicationIdAndVendorAndTierAndDeletedAtIsNull(
        applicationId: String,
        vendor: Vendor,
        tier: ApiKeyTier,
    ): List<ApiKey>

    /** Application별 API 키 목록 (삭제되지 않은 것만) */
    fun findByApplicationIdAndDeletedAtIsNull(applicationId: String): List<ApiKey>

    /** 공용 키 풀 조회 (application_id IS NULL) */
    fun findByApplicationIdIsNullAndVendorAndTierAndDeletedAtIsNull(
        vendor: Vendor,
        tier: ApiKeyTier,
    ): List<ApiKey>

    /** 공용 키 풀 전체 조회 (application_id IS NULL) */
    fun findByApplicationIdIsNullAndVendorAndDeletedAtIsNull(
        vendor: Vendor,
    ): List<ApiKey>
}

