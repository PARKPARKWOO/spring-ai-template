package com.example.demo.adapter.out.persistence

import com.example.demo.model.AiUsageLogs
import org.springframework.data.jpa.repository.JpaRepository

interface AiUsageLogsRepository: JpaRepository<AiUsageLogs, Long> {
}