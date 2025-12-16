package com.example.demo.adapter.`in`.api

import com.example.demo.adapter.out.persistence.ClientRepository
import jakarta.servlet.http.HttpSession
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

data class LoginRequest(
    val name: String,
    val password: String,
)

data class LoginResponse(
    val clientId: Long,
    val clientName: String,
    val message: String,
)

@RestController
@RequestMapping("/api/client")
class ClientController(
    private val clientRepository: ClientRepository,
    private val passwordEncoder: PasswordEncoder,
) {
    @PostMapping("/login")
    fun login(
        @RequestBody request: LoginRequest,
        session: HttpSession,
    ): LoginResponse {
        val client = clientRepository.findByName(request.name)
        
        if (client == null || client.deletedAt != null) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password")
        }
        
        if (!passwordEncoder.matches(request.password, client.password)) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password")
        }
        
        session.setAttribute("clientId", client.id)
        session.setAttribute("clientName", client.name)
        
        return LoginResponse(
            clientId = client.id,
            clientName = client.name,
            message = "Login successful"
        )
    }

    @PostMapping("/logout")
    fun logout(session: HttpSession): Map<String, String> {
        session.invalidate()
        return mapOf("message" to "Logout successful")
    }
}

