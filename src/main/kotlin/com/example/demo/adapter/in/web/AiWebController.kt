package com.example.demo.adapter.`in`.web

import com.example.demo.dto.AiApiRequest
import com.example.demo.model.Vendor
import jakarta.servlet.http.HttpSession
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.servlet.mvc.support.RedirectAttributes

@Controller
@RequestMapping("/ai")
class AiWebController {
    @GetMapping
    fun form(
        model: Model,
        session: HttpSession,
        redirectAttributes: RedirectAttributes,
    ): String {
        val clientId = session.getAttribute("clientId") as? Long
        if (clientId == null) {
            redirectAttributes.addFlashAttribute("error", "Please login first")
            return "redirect:/client/login"
        }
        
        model.addAttribute("vendors", Vendor.entries)
        model.addAttribute("request", AiApiRequest(models = emptyList(), userPrompt = "", sessionId = "1"))
        model.addAttribute("clientName", session.getAttribute("clientName"))
        return "ai-form"
    }
}

