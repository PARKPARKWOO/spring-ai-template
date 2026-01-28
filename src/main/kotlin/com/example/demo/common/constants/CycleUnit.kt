package com.example.demo.common.constants

import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

enum class CycleUnit {
    DAYS, WEEKS, MONTHS;
    
    fun toChronoUnit(): ChronoUnit {
        return when (this) {
            DAYS -> ChronoUnit.DAYS
            WEEKS -> ChronoUnit.WEEKS
            MONTHS -> ChronoUnit.MONTHS
        }
    }
}