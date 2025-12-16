package com.example.demo.adapter.out.persistence

import com.example.demo.model.Client
import org.springframework.data.jpa.repository.JpaRepository

interface ClientRepository: JpaRepository<Client, Long> {
    fun findByName(name: String): Client?
}

