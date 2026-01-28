package com.example.demo

import org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiEmbeddingConnectionAutoConfiguration
import org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiTextEmbeddingAutoConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(
    exclude = [
        GoogleGenAiEmbeddingConnectionAutoConfiguration::class,
        GoogleGenAiTextEmbeddingAutoConfiguration::class
    ]
)
class DemoApplication

fun main(args: Array<String>) {
    runApplication<DemoApplication>(*args)
}
