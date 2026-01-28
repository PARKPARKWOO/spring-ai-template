package com.example.demo.model

/**
 * AI 사용 로그의 콘텐츠 타입을 나타내는 enum
 */
enum class ContentType {
    TEXT,        // 텍스트 기반 AI 모델 호출
    EMBEDDING,   // 임베딩 모델 호출
    VIDEO,       // 비디오 처리
    AUDIO,       // 오디오 처리
    IMAGE;       // 이미지 처리

    companion object {
        fun fromString(value: String): ContentType? {
            return entries.find { it.name.equals(value, ignoreCase = true) }
        }
    }
}
