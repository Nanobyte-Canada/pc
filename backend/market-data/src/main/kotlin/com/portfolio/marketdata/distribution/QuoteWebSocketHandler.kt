package com.portfolio.marketdata.distribution

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.portfolio.common.domain.OptionQuote
import com.portfolio.common.domain.OptionType
import com.portfolio.common.domain.Quote
import com.portfolio.marketdata.streaming.OptionStreamingService
import com.portfolio.marketdata.streaming.QuoteStreamingService
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.math.BigDecimal
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

@Component
class QuoteWebSocketHandler(
    @Lazy private val quoteStreamingService: QuoteStreamingService,
    @Lazy private val optionStreamingService: OptionStreamingService
) : TextWebSocketHandler() {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val objectMapper: ObjectMapper = jacksonObjectMapper().apply { registerModule(JavaTimeModule()) }

    private val subscriptions = ConcurrentHashMap<String, MutableSet<String>>()
    private val symbolToSessions = ConcurrentHashMap<String, MutableSet<String>>()
    private val sessions = ConcurrentHashMap<String, WebSocketSession>()

    private val optionSubscriptions = ConcurrentHashMap<String, MutableSet<String>>()
    private val contractToSessions = ConcurrentHashMap<String, MutableSet<String>>()
    private val chainSubscriptions = ConcurrentHashMap<String, MutableSet<String>>()
    private val chainToSessions = ConcurrentHashMap<String, MutableSet<String>>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        logger.info("WebSocket connection established: {}", session.id)
        sessions[session.id] = session
        subscriptions[session.id] = ConcurrentHashMap.newKeySet()
        optionSubscriptions[session.id] = ConcurrentHashMap.newKeySet()
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        try {
            val tree = objectMapper.readTree(message.payload)
            val action = tree.get("action")?.asText() ?: return

            when (action) {
                "subscribe" -> {
                    val symbol = tree.get("symbol")?.asText() ?: return
                    subscribe(session.id, symbol)
                }
                "unsubscribe" -> {
                    val symbol = tree.get("symbol")?.asText() ?: return
                    unsubscribe(session.id, symbol)
                }
                "subscribe_option" -> {
                    val symbol = tree.get("symbol")?.asText() ?: return
                    val expiry = tree.get("expiry")?.asText() ?: return
                    val strike = tree.get("strike")?.asText() ?: return
                    val optionType = tree.get("optionType")?.asText() ?: return
                    subscribeOption(session.id, symbol, expiry, strike, optionType)
                }
                "unsubscribe_option" -> {
                    val symbol = tree.get("symbol")?.asText() ?: return
                    val expiry = tree.get("expiry")?.asText() ?: return
                    val strike = tree.get("strike")?.asText() ?: return
                    val optionType = tree.get("optionType")?.asText() ?: return
                    unsubscribeOption(session.id, symbol, expiry, strike, optionType)
                }
                "subscribe_chain" -> {
                    val underlying = tree.get("underlying")?.asText() ?: return
                    subscribeChain(session.id, underlying)
                }
                "unsubscribe_chain" -> {
                    val underlying = tree.get("underlying")?.asText() ?: return
                    unsubscribeChain(session.id, underlying)
                }
            }
        } catch (e: Exception) {
            logger.error("Error processing WebSocket message from {}", session.id, e)
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        logger.info("WebSocket connection closed: {}, status: {}", session.id, status)

        subscriptions[session.id]?.forEach { symbol ->
            symbolToSessions[symbol]?.remove(session.id)
            if (symbolToSessions[symbol]?.isEmpty() == true) {
                symbolToSessions.remove(symbol)
                quoteStreamingService.stopStreaming(symbol)
            }
        }

        optionSubscriptions[session.id]?.forEach { contractKey ->
            contractToSessions[contractKey]?.remove(session.id)
            if (contractToSessions[contractKey]?.isEmpty() == true) {
                contractToSessions.remove(contractKey)
                stopOptionStreamingByKey(contractKey)
            }
        }

        chainSubscriptions[session.id]?.forEach { underlying ->
            chainToSessions[underlying]?.remove(session.id)
            if (chainToSessions[underlying]?.isEmpty() == true) {
                chainToSessions.remove(underlying)
                optionStreamingService.stopStreamingChain(underlying)
            }
        }

        subscriptions.remove(session.id)
        optionSubscriptions.remove(session.id)
        chainSubscriptions.remove(session.id)
        sessions.remove(session.id)
    }

    private fun subscribe(sessionId: String, symbol: String) {
        subscriptions[sessionId]?.add(symbol)
        symbolToSessions.computeIfAbsent(symbol) { ConcurrentHashMap.newKeySet() }.add(sessionId)
        quoteStreamingService.startStreaming(symbol)
    }

    private fun unsubscribe(sessionId: String, symbol: String) {
        subscriptions[sessionId]?.remove(symbol)
        symbolToSessions[symbol]?.remove(sessionId)
        if (symbolToSessions[symbol]?.isEmpty() == true) {
            symbolToSessions.remove(symbol)
            quoteStreamingService.stopStreaming(symbol)
        }
    }

    private fun subscribeOption(sessionId: String, symbol: String, expiry: String, strike: String, optionType: String) {
        val contractKey = "$symbol:$expiry:$strike:$optionType"
        optionSubscriptions.computeIfAbsent(sessionId) { ConcurrentHashMap.newKeySet() }.add(contractKey)
        contractToSessions.computeIfAbsent(contractKey) { ConcurrentHashMap.newKeySet() }.add(sessionId)
        val type = if (optionType == "CALL") OptionType.CALL else OptionType.PUT
        optionStreamingService.startStreaming(symbol, LocalDate.parse(expiry), BigDecimal(strike), type)
    }

    private fun unsubscribeOption(sessionId: String, symbol: String, expiry: String, strike: String, optionType: String) {
        val contractKey = "$symbol:$expiry:$strike:$optionType"
        optionSubscriptions[sessionId]?.remove(contractKey)
        contractToSessions[contractKey]?.remove(sessionId)
        if (contractToSessions[contractKey]?.isEmpty() == true) {
            contractToSessions.remove(contractKey)
            val type = if (optionType == "CALL") OptionType.CALL else OptionType.PUT
            optionStreamingService.stopStreaming(symbol, LocalDate.parse(expiry), BigDecimal(strike), type)
        }
    }

    private fun stopOptionStreamingByKey(contractKey: String) {
        val parts = contractKey.split(":")
        if (parts.size == 4) {
            val type = if (parts[3] == "CALL") OptionType.CALL else OptionType.PUT
            optionStreamingService.stopStreaming(parts[0], LocalDate.parse(parts[1]), BigDecimal(parts[2]), type)
        }
    }

    fun broadcastQuote(quote: Quote) {
        val subscribedSessions = symbolToSessions[quote.symbol] ?: return
        val json = try { objectMapper.writeValueAsString(quote) } catch (e: Exception) { return }
        val message = TextMessage(json)
        subscribedSessions.forEach { sessionId ->
            sessions[sessionId]?.let { session ->
                synchronized(session) {
                    try { if (session.isOpen) session.sendMessage(message) } catch (_: Exception) {}
                }
            }
        }
    }

    private fun subscribeChain(sessionId: String, underlying: String) {
        chainSubscriptions.computeIfAbsent(sessionId) { ConcurrentHashMap.newKeySet() }.add(underlying)
        chainToSessions.computeIfAbsent(underlying) { ConcurrentHashMap.newKeySet() }.add(sessionId)
        optionStreamingService.startStreamingChain(underlying)
        logger.info("Session {} subscribed to chain {}", sessionId, underlying)
    }

    private fun unsubscribeChain(sessionId: String, underlying: String) {
        chainSubscriptions[sessionId]?.remove(underlying)
        chainToSessions[underlying]?.remove(sessionId)
        if (chainToSessions[underlying]?.isEmpty() == true) {
            chainToSessions.remove(underlying)
            optionStreamingService.stopStreamingChain(underlying)
        }
        logger.info("Session {} unsubscribed from chain {}", sessionId, underlying)
    }

    fun broadcastOptionQuote(optionQuote: OptionQuote) {
        val contractKey = "${optionQuote.underlying}:${optionQuote.expiry}:${optionQuote.strike}:${optionQuote.optionType}"
        val json = try { objectMapper.writeValueAsString(mapOf(
            "type" to "option_quote",
            "data" to optionQuote
        )) } catch (e: Exception) { return }
        val message = TextMessage(json)

        val recipients = mutableSetOf<String>()
        contractToSessions[contractKey]?.let { recipients.addAll(it) }
        chainToSessions[optionQuote.underlying]?.let { recipients.addAll(it) }
        if (recipients.isEmpty()) return

        recipients.forEach { sessionId ->
            sessions[sessionId]?.let { session ->
                synchronized(session) {
                    try { if (session.isOpen) session.sendMessage(message) } catch (_: Exception) {}
                }
            }
        }
    }

    fun broadcastConnectionStatus(connected: Boolean) {
        val json = try {
            objectMapper.writeValueAsString(
                mapOf(
                    "type" to "connection_status",
                    "connected" to connected,
                    "service" to "market-data"
                )
            )
        } catch (e: Exception) { return }
        val message = TextMessage(json)
        sessions.values.forEach { session ->
            synchronized(session) {
                try { if (session.isOpen) session.sendMessage(message) } catch (_: Exception) {}
            }
        }
    }
}
