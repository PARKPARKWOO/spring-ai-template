package com.example.demo.adapter.`in`.api

import com.example.demo.business.AiService
import com.example.demo.dto.AiApiRequest
import com.example.demo.dto.AiApiResponse
import jakarta.servlet.http.HttpSession
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/ai")
class AiController(
    private val aiService: AiService,
) {
    @PostMapping
    suspend fun call(
        @RequestBody
        request: AiApiRequest,
        session: HttpSession,
    ): List<AiApiResponse> {
        val clientId = session.getAttribute("clientId") as? Long
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please login first")
        
        return aiService.call(request, clientId)
    }
}

