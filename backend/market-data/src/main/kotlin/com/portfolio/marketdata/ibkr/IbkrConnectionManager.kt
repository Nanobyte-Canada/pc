package com.portfolio.marketdata.ibkr

import com.portfolio.marketdata.distribution.QuoteWebSocketHandler
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Component
class IbkrConnectionManager(
    private val ibkrClient: IbkrClient,
    @Lazy private val webSocketHandler: QuoteWebSocketHandler
) : ApplicationRunner {

    private val logger = LoggerFactory.getLogger(IbkrConnectionManager::class.java)
    private val executor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "ibkr-conn-mgr").apply { isDaemon = true }
    }
    private val isHealthy = AtomicBoolean(false)

    private var reconnectDelayMs = 5000L
    private val maxReconnectDelayMs = 60000L
    private val reconnectMultiplier = 2.0
    private val healthCheckIntervalSeconds = 30L

    override fun run(args: ApplicationArguments?) {
        logger.info("IbkrConnectionManager: Starting...")
        ibkrClient.registerDataFarmErrorHandler(Runnable {
            logger.warn("IbkrConnectionManager: Data farm error detected, triggering reconnect")
            ibkrClient.disconnect()
            reconnectDelayMs = 5000L
            scheduleReconnect()
        })
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
        return if (ibkrClient.isConnected()) ConnectionState.CONNECTED else ConnectionState.DISCONNECTED
    }

    fun reconnect() {
        logger.info("IbkrConnectionManager: Manual reconnect requested")
        ibkrClient.disconnect()
        reconnectDelayMs = 5000L
        connectWithRetry()
    }

    private fun connectWithRetry() {
        executor.execute {
            try {
                logger.info("IbkrConnectionManager: Attempting to connect...")
                ibkrClient.connect()
                if (ibkrClient.isConnected()) {
                    logger.info("IbkrConnectionManager: Connected successfully")
                    isHealthy.set(true)
                    reconnectDelayMs = 5000L
                    try { webSocketHandler.broadcastConnectionStatus(true) } catch (_: Exception) {}
                } else {
                    logger.warn("IbkrConnectionManager: Connection failed, will retry")
                    isHealthy.set(false)
                    scheduleReconnect()
                }
            } catch (e: Exception) {
                logger.error("IbkrConnectionManager: Connection failed with exception", e)
                isHealthy.set(false)
                scheduleReconnect()
            }
        }
    }

    private fun checkHealth() {
        val wasHealthy = isHealthy.get()
        val nowConnected = ibkrClient.isConnected()
        isHealthy.set(nowConnected)
        if (wasHealthy && !nowConnected) {
            logger.warn("IbkrConnectionManager: Lost connection, triggering reconnect")
            try { webSocketHandler.broadcastConnectionStatus(false) } catch (_: Exception) {}
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        isHealthy.set(false)
        logger.info("IbkrConnectionManager: Scheduling reconnect in {}ms", reconnectDelayMs)
        executor.schedule({ connectWithRetry() }, reconnectDelayMs, TimeUnit.MILLISECONDS)
        reconnectDelayMs = (reconnectDelayMs * reconnectMultiplier).toLong().coerceAtMost(maxReconnectDelayMs)
    }

    fun shutdown() {
        logger.info("IbkrConnectionManager: Shutting down...")
        try {
            ibkrClient.disconnect()
            executor.shutdownNow()
        } catch (e: Exception) {
            logger.error("Error during shutdown", e)
        }
    }

    enum class ConnectionState { CONNECTED, DISCONNECTED }
}
