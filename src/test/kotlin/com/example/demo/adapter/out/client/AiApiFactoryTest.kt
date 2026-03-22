package com.example.demo.adapter.out.client

import com.example.demo.model.Vendor
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("AiApiFactory")
class AiApiFactoryTest {

    private val anthropicClient = mockk<AbstractAiCallTemplate> {
        every { getVendor() } returns Vendor.ANTHROPIC
    }

    private val geminiClient = mockk<AbstractAiCallTemplate> {
        every { getVendor() } returns Vendor.GOOGLE
    }

    private val openAiClient = mockk<AbstractAiCallTemplate> {
        every { getVendor() } returns Vendor.OPENAI
    }

    private val grokClient = mockk<AbstractAiCallTemplate> {
        every { getVendor() } returns Vendor.X_AI
    }

    private val factory = AiApiFactory(listOf(anthropicClient, geminiClient, openAiClient, grokClient))

    @Test
    @DisplayName("ANTHROPIC 벤더로 AnthropicClient를 반환한다")
    fun `returns anthropic client for ANTHROPIC vendor`() {
        val client = factory.getClient(Vendor.ANTHROPIC)
        assertEquals(Vendor.ANTHROPIC, client.getVendor())
        assertSame(anthropicClient, client)
    }

    @Test
    @DisplayName("GOOGLE 벤더로 GeminiClient를 반환한다")
    fun `returns gemini client for GOOGLE vendor`() {
        val client = factory.getClient(Vendor.GOOGLE)
        assertEquals(Vendor.GOOGLE, client.getVendor())
        assertSame(geminiClient, client)
    }

    @Test
    @DisplayName("OPENAI 벤더로 OpenAiClient를 반환한다")
    fun `returns openai client for OPENAI vendor`() {
        val client = factory.getClient(Vendor.OPENAI)
        assertEquals(Vendor.OPENAI, client.getVendor())
        assertSame(openAiClient, client)
    }

    @Test
    @DisplayName("X_AI 벤더로 GrokClient를 반환한다")
    fun `returns grok client for X_AI vendor`() {
        val client = factory.getClient(Vendor.X_AI)
        assertEquals(Vendor.X_AI, client.getVendor())
        assertSame(grokClient, client)
    }

    @Test
    @DisplayName("등록되지 않은 벤더 조회 시 예외를 발생시킨다")
    fun `throws when vendor not registered`() {
        val emptyFactory = AiApiFactory(emptyList())
        assertThrows<NoSuchElementException> {
            emptyFactory.getClient(Vendor.ANTHROPIC)
        }
    }

    @Test
    @DisplayName("모든 벤더가 등록되어 있다")
    fun `all vendors are registered`() {
        Vendor.entries.forEach { vendor ->
            val client = factory.getClient(vendor)
            assertEquals(vendor, client.getVendor())
        }
    }
}
