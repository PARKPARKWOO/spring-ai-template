package com.example.demo.dto

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 벤더별 옵션의 공통 마커 인터페이스
 * Chat과 Embedding 옵션의 공통 타입을 제공합니다.
 */
@Schema(description = "벤더별 옵션 기본 인터페이스")
interface VendorOptionsBase
