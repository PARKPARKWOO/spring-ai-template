package com.example.demo.adapter.out.client

import com.example.demo.dto.ImageSource
import com.example.demo.dto.VisionResponse
import com.example.demo.dto.AiCallContext
import com.example.demo.model.Vendor

/**
 * Vision(멀티모달 이미지 입력) 호출 포트.
 *
 * 기존 [AiCallPort] 의 chat/stream 과 별개의 호출 경로로,
 * 텍스트 메시지 + 이미지 리스트를 함께 전달하여 응답을 받는다.
 */
interface VisionCallPort {
    fun getVendor(): Vendor
    suspend fun call(context: AiCallContext, images: List<ImageSource>): VisionResponse
}
