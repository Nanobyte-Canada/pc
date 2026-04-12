package com.portfolio.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class HealthCheckMdcFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if (request.requestURI == "/health") {
            MDC.put("skipLog", "true")
            try {
                filterChain.doFilter(request, response)
            } finally {
                MDC.remove("skipLog")
            }
        } else {
            filterChain.doFilter(request, response)
        }
    }
}
