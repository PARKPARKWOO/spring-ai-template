package com.example.demo.dto

import com.example.demo.model.Vendor

data class AiApiResponse(
    val vendor: Vendor,
    val result: String,
)
