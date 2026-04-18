package com.portfolio.marketdata.ibkr

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Component
class IbkrConnectionManager(
    private val ibkrClient: IbkrClient
) : ApplicationRunner {

    private val logger = LoggerFactory.getLogger(IbkrConnectionManager::class.java)
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private val isHealthy = AtomicBoolean(false)

    private var reconnectDelayMs = 5000L
    private val maxReconnectDelayMs = 60000L
    private val reconnectMultiplier = 2.0

    override fun run(args: ApplicationArguments?) {
        logger.info("IbkrConnectionManager: Starting...")
        connectWithRetry()
    }

    fun isHealthy(): Boolean = isHealthy.get()

    fun getConnectionState(): ConnectionState {
        return if (ibkrClient.isConnected()) ConnectionState.CONNECTED else ConnectionState.DISCONNECTED
    }

    fun reconnect() {
        logger.info("IbkrConnectionManager: Manual reconnect requested")
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
                } else {
                    logger.warn("IbkrConnectionManager: Connection failed, will retry")
                    scheduleReconnect()
                }
            } catch (e: Exception) {
                logger.error("IbkrConnectionManager: Connection failed with exception", e)
                isHealthy.set(false)
                scheduleReconnect()
            }
        }
    }

    private fun scheduleReconnect() {
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
