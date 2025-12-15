package com.example.demo.model

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id

@Entity
class Quota (
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private val id: Long,
    private val clientId: Long,
    private val vendor: String,
    private val amount: Long,
){
}