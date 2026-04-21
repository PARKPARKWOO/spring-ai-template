package com.example.demo.adapter.out.client

import com.example.demo.business.exception.AiServiceException
import com.example.demo.model.ApiErrorCode
import com.example.demo.model.Vendor
import org.springframework.stereotype.Component

/**
 * Vendor 별 [VisionCallPort] 라우팅.
 *
 * 현재는 Google(Gemini)만 구현되어 있고, OpenAI/Anthropic/xAI 는
 * 후속에 추가될 수 있다.
 */
@Component
class VisionFactory(
    private val ports: List<VisionCallPort>,
) {
    private val byVendor: Map<Vendor, VisionCallPort> = ports.associateBy { it.getVendor() }

    fun getClient(vendor: Vendor): VisionCallPort =
        byVendor[vendor] ?: throw AiServiceException(
            ApiErrorCode.AI_VENDOR_NOT_SUPPORTED,
            "Vision API는 아직 $vendor 벤더를 지원하지 않습니다. 지원 벤더: ${byVendor.keys}",
        )
}
