package com.example.demo.common.config

import com.example.demo.adapter.`in`.filter.AppKeyAuthenticationFilter
import com.example.demo.adapter.`in`.filter.JwtAuthenticationFilter
import com.example.demo.adapter.`in`.resolver.ClientInfoArgumentResolver
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val appKeyAuthenticationFilter: AppKeyAuthenticationFilter,
    private val clientInfoArgumentResolver: ClientInfoArgumentResolver
) : WebMvcConfigurer {
    /**
     * JWT 인증 필터 등록
     * AppKey 필터보다 먼저 실행되어야 함 (AppKey 필터에서 JWT 인증 여부를 확인하기 위해)
     */
    @Bean
    fun jwtFilterRegistration(): FilterRegistrationBean<JwtAuthenticationFilter> {
        val registration = FilterRegistrationBean<JwtAuthenticationFilter>()
        registration.filter = jwtAuthenticationFilter
        registration.addUrlPatterns("/api/*") // API 엔드포인트에만 적용
        registration.order = Ordered.HIGHEST_PRECEDENCE // JWT 필터를 먼저 실행
        return registration
    }

    /**
     * AppKey 인증 필터 등록
     * JWT 필터 이후에 실행되어 충돌을 감지할 수 있도록 함
     */
    @Bean
    fun appKeyFilterRegistration(): FilterRegistrationBean<AppKeyAuthenticationFilter> {
        val registration = FilterRegistrationBean<AppKeyAuthenticationFilter>()
        registration.filter = appKeyAuthenticationFilter
        registration.addUrlPatterns("/api/*") // API 엔드포인트에만 적용
        registration.order = Ordered.HIGHEST_PRECEDENCE + 1 // JWT 필터 이후 실행
        return registration
    }

    /**
     * Argument Resolver 등록
     */
    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(clientInfoArgumentResolver)
    }
}
