package com.example.demo.adapter.persistence

import com.example.demo.model.Quota
import org.springframework.data.jpa.repository.JpaRepository

interface QuotaRepository: JpaRepository<Quota, Long> {
}