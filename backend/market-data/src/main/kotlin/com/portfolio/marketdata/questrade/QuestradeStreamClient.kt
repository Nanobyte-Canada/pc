package com.portfolio.marketdata.questrade

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

// Tick-type codes of the IBKR-compatible protocol consumed by QuoteStreamingService/OptionStreamingService.
private const val TICK_BID = 1
private const val TICK_ASK = 2
private const val TICK_LAST = 4
private const val TICK_VOLUME = 8

/** Shared mapper for stream frames; ObjectMapper is thread-safe for read operations. */
private val FRAME_MAPPER = ObjectMapper()

/**
 * Parses one complete WebSocket text frame into (symbolId -> quote) pairs.
 * Stock frames carry a "quotes" array, option frames an "optionQuotes" array;
 * control frames such as {"success":true} yield two empty lists.
 */
fun parseQuoteFrame(json: String): Pair<List<Pair<Int, QuestradeStockQuote>>, List<Pair<Int, QuestradeOptionQuote>>> {
    val root = try {
        FRAME_MAPPER.readTree(json)
    } catch (_: Exception) {
        return emptyList<Pair<Int, QuestradeStockQuote>>() to emptyList()
    }
    val stocks = root.path("quotes").mapNotNull { if (it.isObject) it.toStockQuote() else null }.map { it.symbolId to it }
    val options = root.path("optionQuotes").mapNotNull { if (it.isObject) it.toOptionQuote() else null }.map { it.symbolId to it }
    return stocks to options
}

/** Maps bid→(1,v), ask→(2,v), last→(4,v), volume→(8,v); null fields skipped, zero volume skipped. */
fun synthesizeTicks(q: QuestradeStockQuote): List<Pair<Int, Double>> = buildList {
    q.bidPrice?.let { add(TICK_BID to it) }
    q.askPrice?.let { add(TICK_ASK to it) }
    q.lastTradePrice?.let { add(TICK_LAST to it) }
    if (q.volume > 0) add(TICK_VOLUME to q.volume.toDouble())
}

/** Same mapping as the stock overload; greeks/IV are not delivered as stream ticks. */
fun synthesizeTicks(q: QuestradeOptionQuote): List<Pair<Int, Double>> = buildList {
    q.bidPrice?.let { add(TICK_BID to it) }
    q.askPrice?.let { add(TICK_ASK to it) }
    q.lastTradePrice?.let { add(TICK_LAST to it) }
    if (q.volume > 0) add(TICK_VOLUME to q.volume.toDouble())
}

private fun JsonNode.toStockQuote() = QuestradeStockQuote(
    symbolId = path("symbolId").asInt(0),
    bidPrice = doubleOrNull("bidPrice"),
    askPrice = doubleOrNull("askPrice"),
    lastTradePrice = doubleOrNull("lastTradePrice"),
    volume = path("volume").asLong(0),
    delay = path("delay").asInt(0)
)

private fun JsonNode.toOptionQuote() = QuestradeOptionQuote(
    symbolId = path("symbolId").asInt(0),
    underlyingId = path("underlyingId").asInt(0),
    bidPrice = doubleOrNull("bidPrice"),
    askPrice = doubleOrNull("askPrice"),
    lastTradePrice = doubleOrNull("lastTradePrice"),
    volume = path("volume").asLong(0),
    openInterest = path("openInterest").asLong(0),
    volatility = doubleOrNull("volatility"),
    delta = doubleOrNull("delta"),
    gamma = doubleOrNull("gamma"),
    theta = doubleOrNull("theta"),
    vega = doubleOrNull("vega"),
    rho = doubleOrNull("rho"),
    delay = path("delay").asInt(0)
)

private fun JsonNode.doubleOrNull(field: String): Double? =
    path(field).takeIf { !it.isMissingNode && !it.isNull && it.isNumber }?.asDouble()

/**
 * Single-WebSocket multiplexer for Questrade stock+option quote streams.
 *
 * One socket total: every reconcile renegotiates ONE session for the whole desired set —
 * OPTIONS mode when any option id is subscribed, otherwise EQUITY mode. While in OPTIONS
 * mode, equity ids not covered by incoming frames stay live via a REST poll fallback
 * ([QuestradeProperties.equityPollIntervalSeconds]).
 *
 * Routing note: [subscribe] receives only a bare conId (the provider-neutral interface has
 * no sec-type), so equities/options are classified by Questrade's id convention — option
 * symbolIds are 8+ digits, equity symbolIds stay far below [OPTION_ID_THRESHOLD].
 */
@Component
class QuestradeStreamClient(
    private val restClient: QuestradeRestClient,
    private val tokenManager: QuestradeTokenManager,
    private val properties: QuestradeProperties
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Desired subscription sets keyed by Questrade symbolId. */
    private val equityCallbacks = ConcurrentHashMap<Int, (Int, Double) -> Unit>()
    private val optionCallbacks = ConcurrentHashMap<Int, (Int, Double) -> Unit>()

    /** Equity ids seen in incoming frames during OPTIONS mode; excluded from the poll fallback. */
    private val coveredEquityIds: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    private val reconnectHandlers = CopyOnWriteArrayList<Runnable>()
    private val backoffSeconds = AtomicInteger(RECONNECT_INITIAL_BACKOFF_SECONDS)

    /** Reconciles, retries, polls and keepalives all serialize on this single thread. */
    private val scheduler = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "qt-stream-client") }
    @Volatile private var pendingReconcile: ScheduledFuture<*>? = null

    private val httpClient: HttpClient = HttpClient.newHttpClient()
    @Volatile private var webSocket: WebSocket? = null
    @Volatile private var connected = false
    @Volatile private var optionsMode = false
    @Volatile private var shutdown = false

    init {
        scheduler.scheduleWithFixedDelay({ pollUncoveredEquities() },
            properties.equityPollIntervalSeconds, properties.equityPollIntervalSeconds, TimeUnit.SECONDS)
        scheduler.scheduleWithFixedDelay({ keepalive() },
            properties.keepaliveMinutes, properties.keepaliveMinutes, TimeUnit.MINUTES)
    }

    fun subscribe(conId: Int, callback: (tickType: Int, value: Double) -> Unit) {
        if (conId >= OPTION_ID_THRESHOLD) optionCallbacks[conId] = callback else equityCallbacks[conId] = callback
        scheduleReconcile()
    }

    fun unsubscribe(conId: Int) {
        equityCallbacks.remove(conId)
        optionCallbacks.remove(conId)
        coveredEquityIds.remove(conId)
        scheduleReconcile()
    }

    fun isConnected(): Boolean = connected

    /** Handlers run after each successful (re)connect; re-requesting subscriptions upstream is idempotent. */
    fun setReconnectHandler(handler: Runnable) {
        reconnectHandlers.add(handler)
    }

    fun shutdown() {
        shutdown = true
        scheduler.shutdownNow()
        closeSocketQuietly()
    }

    /** Debounces subscribe/unsubscribe bursts into exactly one renegotiate 750ms after the last change. */
    private fun scheduleReconcile() {
        pendingReconcile?.cancel(false)
        pendingReconcile = scheduler.schedule({ reconcile() }, RECONCILE_DEBOUNCE_MS, TimeUnit.MILLISECONDS)
    }

    /** Exponential backoff retry (5s doubling to a 60s cap); reset on successful open. */
    private fun scheduleRetry() {
        connected = false
        val delaySeconds = backoffSeconds.getAndUpdate { cur -> (cur * 2).coerceAtMost(RECONNECT_MAX_BACKOFF_SECONDS) }
        log.warn("Reconnecting Questrade stream in {}s", delaySeconds)
        scheduler.schedule({ reconcile() }, delaySeconds.toLong(), TimeUnit.SECONDS)
    }

    private fun reconcile() {
        if (shutdown) return
        val optionIds = optionCallbacks.keys.toList()
        val equityIds = equityCallbacks.keys.toList()
        if (optionIds.isEmpty() && equityIds.isEmpty()) {
            closeSocketQuietly()
            return
        }
        val wantOptionsMode = optionIds.isNotEmpty()
        val port = try {
            if (wantOptionsMode) restClient.negotiateOptionStream(optionIds) else restClient.negotiateStockStream(equityIds)
        } catch (e: Exception) {
            log.warn("Stream negotiation failed; retrying with backoff", e)
            scheduleRetry(); return
        }
        if (port <= 0) {
            log.warn("Stream negotiation returned port {}; retrying with backoff", port)
            scheduleRetry(); return
        }
        optionsMode = wantOptionsMode
        coveredEquityIds.clear()
        closeSocketQuietly() // single-socket invariant: drop the prior session before opening the next
        val token = try {
            tokenManager.getValidAccessToken()
        } catch (e: Exception) {
            log.warn("Access token unavailable for stream connect; retrying with backoff", e)
            scheduleRetry(); return
        }
        val host = token.apiServer.trimEnd('/').removePrefix("https://")
        log.info("Opening Questrade {} stream wss://{}:{} ({} equity / {} option subscriptions)",
            if (wantOptionsMode) "option" else "stock", host, port, equityIds.size, optionIds.size)
        httpClient.newWebSocketBuilder().buildAsync(URI("wss://$host:$port"), StreamListener())
            .whenComplete { _, error ->
                if (error != null && !shutdown) {
                    log.warn("WebSocket connect failed: {}", error.toString())
                    scheduleRetry()
                }
            }
    }

    private fun dispatchFrame(frameJson: String) {
        val (stocks, options) = parseQuoteFrame(frameJson)
        for ((symbolId, quote) in stocks) {
            coveredEquityIds.add(symbolId)
            dispatch(equityCallbacks[symbolId], synthesizeTicks(quote))
        }
        for ((_, quote) in options) {
            dispatch(optionCallbacks[quote.symbolId], synthesizeTicks(quote))
        }
    }

    /** Skips unregistered ids; one bad callback must not kill the dispatch loop. */
    private fun dispatch(callback: ((Int, Double) -> Unit)?, ticks: List<Pair<Int, Double>>) {
        callback ?: return
        for ((tickType, value) in ticks) {
            try {
                callback(tickType, value)
            } catch (e: Exception) {
                log.error("Subscriber callback failed for tick type {}: {}", tickType, e.toString())
            }
        }
    }

    /** OPTIONS-mode fallback: equities absent from stream frames stay live via batched REST polling. */
    private fun pollUncoveredEquities() {
        if (shutdown || !connected || !optionsMode) return
        val uncovered = equityCallbacks.keys.filter { it !in coveredEquityIds }
        if (uncovered.isEmpty()) return
        try {
            restClient.getStockQuotes(uncovered).forEach { quote ->
                dispatch(equityCallbacks[quote.symbolId], synthesizeTicks(quote))
            }
        } catch (e: Exception) {
            log.debug("Equity fallback poll failed: {}", e.toString())
        }
    }

    /** Questrade drops sessions without at least one REST call per 30 minutes. */
    private fun keepalive() {
        if (shutdown) return
        try {
            restClient.serverTime()
        } catch (e: Exception) {
            log.debug("Keepalive serverTime call failed: {}", e.toString())
        }
    }

    private fun closeSocketQuietly() {
        val ws = webSocket ?: return
        webSocket = null
        connected = false
        try {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "renegotiating")
        } catch (_: Exception) {
            // Already dead; nothing to do.
        }
    }

    private inner class StreamListener : WebSocket.Listener {
        private val buffer = StringBuilder()

        override fun onOpen(webSocket: WebSocket) {
            this@QuestradeStreamClient.webSocket = webSocket
            // Questrade requires the raw access token as the very first text message.
            webSocket.sendText(tokenManager.getValidAccessToken().token, true)
            webSocket.request(1)
            connected = true
            backoffSeconds.set(RECONNECT_INITIAL_BACKOFF_SECONDS)
            log.info("Questrade stream connected")
            reconnectHandlers.forEach { handler ->
                try {
                    handler.run()
                } catch (e: Exception) {
                    log.error("Reconnect handler failed", e)
                }
            }
        }

        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            buffer.append(data)
            if (last) {
                val frame = buffer.toString()
                buffer.setLength(0)
                dispatchFrame(frame)
            }
            webSocket.request(1)
            return null
        }

        override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*>? {
            if (this@QuestradeStreamClient.webSocket !== webSocket) return null // stale session
            connected = false
            if (!shutdown && statusCode != WebSocket.NORMAL_CLOSURE) {
                log.warn("Questrade stream closed abnormally (code={}, reason={})", statusCode, reason)
                scheduleRetry()
            }
            return null
        }

        override fun onError(webSocket: WebSocket, error: Throwable) {
            if (this@QuestradeStreamClient.webSocket !== webSocket) return // stale session
            connected = false
            if (!shutdown) {
                log.warn("Questrade stream error: {}", error.toString())
                scheduleRetry()
            }
        }
    }

    companion object {
        /** Option symbolIds are 8+ digits; equity symbolIds stay well below this bound. */
        private const val OPTION_ID_THRESHOLD = 10_000_000
        private const val RECONCILE_DEBOUNCE_MS = 750L
        private const val RECONNECT_INITIAL_BACKOFF_SECONDS = 5
        private const val RECONNECT_MAX_BACKOFF_SECONDS = 60
    }
}
