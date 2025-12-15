package com.example.demo.adapter.persistence

import com.example.demo.model.AiUsageLogs
import org.springframework.data.jpa.repository.JpaRepository

interface AiUsageLogsRepository: JpaRepository<AiUsageLogs, Long> {
}