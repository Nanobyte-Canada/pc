package com.portfolio.brokergateway.adapter.ibkr

import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class IbkrConnectionManager(
    private val client: IbkrAccountClient,
    private val config: IbkrConfig
) {
    private val logger = LoggerFactory.getLogger(IbkrConnectionManager::class.java)
    private val healthy = AtomicBoolean(false)
    private val currentDelay = AtomicLong(config.reconnectDelayMs)
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "ibkr-reconnect").apply { isDaemon = true }
    }

    fun start() {
        logger.info("Starting IBKR connection manager (host={}, port={}, clientId={})",
            config.host, config.port, config.clientId)
        connectWithRetry()
    }

    fun isHealthy(): Boolean = healthy.get()

    fun shutdown() {
        logger.info("Shutting down IBKR connection manager")
        healthy.set(false)
        try {
            client.disconnect()
        } catch (e: Exception) {
            logger.warn("Error disconnecting IBKR client", e)
        }
        executor.shutdownNow()
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun connectWithRetry() {
        try {
            client.connect()
            if (client.isConnected()) {
                healthy.set(true)
                currentDelay.set(config.reconnectDelayMs)
                logger.info("IBKR connection established")
            } else {
                logger.warn("IBKR client connect() returned but isConnected() is false")
                scheduleReconnect()
            }
        } catch (e: Exception) {
            logger.error("Failed to connect to IBKR: {}", e.message, e)
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        healthy.set(false)
        val delay = currentDelay.get()
        logger.info("Scheduling IBKR reconnect in {}ms", delay)
        executor.schedule({
            connectWithRetry()
        }, delay, TimeUnit.MILLISECONDS)
        // Exponential backoff: double delay, capped at max
        val nextDelay = (delay * 2).coerceAtMost(config.maxReconnectDelayMs)
        currentDelay.set(nextDelay)
    }
}
