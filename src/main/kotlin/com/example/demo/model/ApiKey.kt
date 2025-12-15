package com.example.demo.model

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import java.time.LocalDateTime

@Entity
class ApiKey (
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private val id: Long,
    private val key: String,
    private val description: String,
    private val createdAt: LocalDateTime,
    private val updatedAt: LocalDateTime,
    private val deletedAt: LocalDateTime?,
){
}