package com.example.demo.common.config

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.woo.apm.pyroscope.EnablePyroscope
import org.woo.apm.log.config.TracingConfig

/**
 * APM 설정 (Eureka·Pyroscope 연동).
 * - Pyroscope: 프로파일링 수집 (PYROSCOPE_SERVER_ADDRESS)
 * - TracingConfig: Trace ID 등 로깅 컨텍스트
 */
@EnablePyroscope
@Configuration
@Import(TracingConfig::class)
class ApmConfig
