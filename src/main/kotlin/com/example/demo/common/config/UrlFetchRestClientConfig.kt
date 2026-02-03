package com.example.demo.common.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration

/**
 * URL 콘텐츠 조회용 RestClient 설정.
 * - Spring RestClient + JdkClientHttpRequestFactory 사용
 * - 301/302 리다이렉트 자동 추적 (followRedirects = NORMAL)
 * - GeminiClient의 url_contexts 등에서 사용
 */
@Configuration
class UrlFetchRestClientConfig {

    @Bean
    fun urlFetchRestClient(): RestClient {
        val jdkHttpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL) // 301, 302 등 리다이렉트 자동 추적
            .build()
        val requestFactory = JdkClientHttpRequestFactory(jdkHttpClient).apply {
            setReadTimeout(Duration.ofSeconds(10))
        }
        return RestClient.builder()
            .requestFactory(requestFactory)
            .defaultHeader("User-Agent", "Mozilla/5.0 (compatible; AI-Bot/1.0)")
            .build()
    }
}
