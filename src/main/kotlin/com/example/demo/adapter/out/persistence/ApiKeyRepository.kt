package com.example.demo.adapter.out.persistence

import com.example.demo.model.ApiKeyTier
import com.example.demo.model.Vendor
import com.example.demo.model.api.ApiKey
import org.springframework.data.jpa.repository.JpaRepository

interface ApiKeyRepository : JpaRepository<ApiKey, Long> {
    /** Application별 벤더 API 키 목록 조회 (삭제되지 않은 것만, id ASC 안정 정렬) */
    fun findByApplicationIdAndVendorAndDeletedAtIsNullOrderByIdAsc(
        applicationId: String,
        vendor: Vendor,
    ): List<ApiKey>

    /** Application별 벤더 + 티어 API 키 목록 조회 (id ASC 안정 정렬) */
    fun findByApplicationIdAndVendorAndTierAndDeletedAtIsNullOrderByIdAsc(
        applicationId: String,
        vendor: Vendor,
        tier: ApiKeyTier,
    ): List<ApiKey>

    /** Application별 API 키 목록 (삭제되지 않은 것만) */
    fun findByApplicationIdAndDeletedAtIsNull(applicationId: String): List<ApiKey>

    /** 전체 API 키 목록 (삭제되지 않은 것만) - admin 조회용 */
    fun findAllByDeletedAtIsNull(): List<ApiKey>

    /** 공용 키 풀 조회 (application_id IS NULL, id ASC 안정 정렬) */
    fun findByApplicationIdIsNullAndVendorAndTierAndDeletedAtIsNullOrderByIdAsc(
        vendor: Vendor,
        tier: ApiKeyTier,
    ): List<ApiKey>

    /** 공용 키 풀 전체 조회 (application_id IS NULL) */
    fun findByApplicationIdIsNullAndVendorAndDeletedAtIsNull(
        vendor: Vendor,
    ): List<ApiKey>
}
