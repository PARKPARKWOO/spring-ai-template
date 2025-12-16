package com.example.demo.adapter.out.persistence

import com.example.demo.model.api.ApiKey
import org.springframework.data.jpa.repository.JpaRepository

interface ApiKeyRepository: JpaRepository<ApiKey, Long> {
    fun findByVendor(vendor: com.example.demo.model.Vendor): ApiKey?
}

