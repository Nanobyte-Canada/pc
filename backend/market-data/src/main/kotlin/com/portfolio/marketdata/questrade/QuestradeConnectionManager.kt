package com.portfolio.marketdata.questrade

import com.portfolio.marketdata.distribution.QuoteWebSocketHandler
import com.portfolio.marketdata.provider.MarketDataProvider
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Component
class QuestradeConnectionManager(
    private val provider: MarketDataProvider,
    @Lazy private val webSocketHandler: QuoteWebSocketHandler
) : ApplicationRunner {

    private val logger = LoggerFactory.getLogger(QuestradeConnectionManager::class.java)
    private val executor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "qt-conn-mgr").apply { isDaemon = true }
    }
    private val isHealthy = AtomicBoolean(false)

    private var reconnectDelayMs = 5000L
    private val maxReconnectDelayMs = 60000L
    private val reconnectMultiplier = 2.0
    private val healthCheckIntervalSeconds = 30L

    override fun run(args: ApplicationArguments?) {
        logger.info("QuestradeConnectionManager: Starting...")
        connectWithRetry()
        executor.scheduleWithFixedDelay(
            { checkHealth() },
            healthCheckIntervalSeconds,
            healthCheckIntervalSeconds,
            TimeUnit.SECONDS
        )
    }

    fun isHealthy(): Boolean = isHealthy.get()

    fun getConnectionState(): ConnectionState {
        return if (provider.isConnected()) ConnectionState.CONNECTED else ConnectionState.DISCONNECTED
    }

    fun reconnect() {
        logger.info("QuestradeConnectionManager: Manual reconnect requested")
        provider.disconnect()
        reconnectDelayMs = 5000L
        connectWithRetry()
    }

    private fun connectWithRetry() {
        executor.execute {
            try {
                logger.info("QuestradeConnectionManager: Attempting to connect...")
                provider.connect()
                if (provider.isConnected()) {
                    logger.info("QuestradeConnectionManager: Connected successfully")
                    isHealthy.set(true)
                    reconnectDelayMs = 5000L
                    try { webSocketHandler.broadcastConnectionStatus(true) } catch (_: Exception) {}
                } else {
                    logger.warn("QuestradeConnectionManager: Connection failed, will retry")
                    isHealthy.set(false)
                    scheduleReconnect()
                }
            } catch (e: Exception) {
                logger.error("QuestradeConnectionManager: Connection failed with exception", e)
                isHealthy.set(false)
                scheduleReconnect()
            }
        }
    }

    private fun checkHealth() {
        val wasHealthy = isHealthy.get()
        val nowConnected = provider.isConnected()
        isHealthy.set(nowConnected)
        if (wasHealthy && !nowConnected) {
            logger.warn("QuestradeConnectionManager: Lost connection, triggering reconnect")
            try { webSocketHandler.broadcastConnectionStatus(false) } catch (_: Exception) {}
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        isHealthy.set(false)
        logger.info("QuestradeConnectionManager: Scheduling reconnect in {}ms", reconnectDelayMs)
        executor.schedule({ connectWithRetry() }, reconnectDelayMs, TimeUnit.MILLISECONDS)
        reconnectDelayMs = (reconnectDelayMs * reconnectMultiplier).toLong().coerceAtMost(maxReconnectDelayMs)
    }

    fun shutdown() {
        logger.info("QuestradeConnectionManager: Shutting down...")
        try {
            provider.disconnect()
            executor.shutdownNow()
        } catch (e: Exception) {
            logger.error("Error during shutdown", e)
        }
    }

    enum class ConnectionState { CONNECTED, DISCONNECTED }
}
