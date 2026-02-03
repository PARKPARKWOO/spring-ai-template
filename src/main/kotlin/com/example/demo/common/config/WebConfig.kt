package com.example.demo.common.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig : WebMvcConfigurer {
    // 인증 필터/리졸버 제거됨 — 호출 서비스에서 applicationId 전달
}
