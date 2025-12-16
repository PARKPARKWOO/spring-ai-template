package com.example.demo.adapter.`in`.web

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping

@Controller
@RequestMapping
class ClientWebController {
    @GetMapping("/")
    fun index(): String {
        return "redirect:/client/login"
    }
    
    @GetMapping("/client/login")
    fun loginPage(model: Model): String {
        return "login"
    }
}

