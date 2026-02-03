package com.example.demo.model

enum class Vendor {
    OPENAI, ANTHROPIC, GOOGLE, X_AI;

    companion object {
        fun contains(vendor: Vendor): Boolean =
            Vendor.entries.find { v -> v == vendor } != null

        fun contains(vendor: String): Boolean = runCatching {
            Vendor.valueOf(vendor)
        }.getOrNull() == null
    }
}