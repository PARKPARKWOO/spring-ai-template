package com.example.demo.adapter.`in`.web

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping

/**
 * 백오피스 웹 컨트롤러
 * AppKey 관리 및 클라이언트 관리 UI 제공
 */
@Controller
@RequestMapping("/backoffice")
class BackofficeWebController {

    @GetMapping
    fun index(): String {
        return "redirect:/backoffice/dashboard"
    }

    @GetMapping("/dashboard")
    fun dashboard(model: Model): String {
        return "backoffice/dashboard"
    }

    @GetMapping("/app-keys")
    fun appKeysPage(model: Model): String {
        return "backoffice/app-keys"
    }

    @GetMapping("/clients")
    fun clientsPage(model: Model): String {
        return "backoffice/clients"
    }

    @GetMapping("/api-keys")
    fun apiKeysPage(model: Model): String {
        return "backoffice/api-keys"
    }

    @GetMapping("/token-pricing-policies")
    fun tokenPricingPoliciesPage(model: Model): String {
        return "backoffice/token-pricing-policies"
    }

    @GetMapping("/clients/{clientId}/token-quotas")
    fun clientTokenQuotasPage(@PathVariable clientId: Long, model: Model): String {
        model.addAttribute("clientId", clientId)
        return "backoffice/client-token-quotas"
    }

    @GetMapping("/clients/{clientId}/pricing-quotas")
    fun clientPricingQuotasPage(@PathVariable clientId: Long, model: Model): String {
        model.addAttribute("clientId", clientId)
        return "backoffice/client-pricing-quotas"
    }
}
