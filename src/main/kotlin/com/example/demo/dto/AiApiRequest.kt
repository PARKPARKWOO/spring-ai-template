package com.example.demo.dto

import com.example.demo.model.Vendor

data class AiApiRequest(
    val vendor: List<Vendor>,
    val userPrompt: String,
)
