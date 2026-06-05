package com.portfolio.marketdata.config

import com.portfolio.marketdata.distribution.QuoteWebSocketHandler
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

@Configuration
@EnableWebSocket
class WebSocketConfig(
    private val quoteWebSocketHandler: QuoteWebSocketHandler
) : WebSocketConfigurer {

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(quoteWebSocketHandler, "/ws/quotes")
            .setAllowedOrigins("*")
    }
}
