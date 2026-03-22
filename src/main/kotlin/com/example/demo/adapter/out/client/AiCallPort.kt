package com.example.demo.adapter.out.client

import com.example.demo.dto.AiCallContext
import com.example.demo.dto.AiApiResponse
import com.example.demo.model.Vendor
import reactor.core.publisher.Flux

/**
 * AI 벤더 호출 포트.
 *
 * AiCallContext 하나로 모든 호출 파라미터를 전달받아
 * 벤더별 구현체에서 필요한 옵션만 추출하여 사용한다.
 * 새로운 옵션 추가 시 인터페이스 시그니처 변경 없이 AiCallContext에만 필드를 추가하면 된다.
 */
interface AiCallPort {
    fun getVendor(): Vendor
    suspend fun call(context: AiCallContext): AiApiResponse
    suspend fun stream(context: AiCallContext): Flux<String>
}
