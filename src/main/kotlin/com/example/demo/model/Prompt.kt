package com.example.demo.model

data class Prompt(
    val systemPrompt: String,
    val userPrompt: String,
) {
    fun getMessage(): String{
        return systemPrompt + userPrompt
    }
}