package com.portfolio.broker.scheduler

import com.portfolio.broker.repository.BrokerConnectionRepository
import com.portfolio.broker.service.PositionFetchService
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Scheduled job that fetches positions for users with auto-fetch enabled.
 * Runs every hour and processes users whose preferred fetch time matches the current hour.
 */
@Component
@ConditionalOnProperty(prefix = "broker.scheduler", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class DailyPositionFetchScheduler(
    private val connectionRepository: BrokerConnectionRepository,
    private val positionFetchService: PositionFetchService,
    private val meterRegistry: MeterRegistry
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val fetchesScheduledCounter = meterRegistry.counter("broker_scheduled_fetches_total")
    private val fetchesSuccessCounter = meterRegistry.counter("broker_scheduled_fetches_success")
    private val fetchesErrorCounter = meterRegistry.counter("broker_scheduled_fetches_error")

    /**
     * Runs every hour at :00 to check for users who need their positions fetched.
     */
    @Scheduled(cron = "0 0 * * * *")
    fun scheduledPositionFetch() {
        val currentHourUtc = OffsetDateTime.now(ZoneOffset.UTC).hour

        log.info("Running scheduled position fetch for hour {} UTC", currentHourUtc)

        try {
            // Find active connections for users with auto-fetch enabled at this hour
            val connections = connectionRepository.findConnectionsForScheduledFetch(currentHourUtc)

            if (connections.isEmpty()) {
                log.info("No connections scheduled for fetch at hour {} UTC", currentHourUtc)
                return
            }

            log.info("Found {} connections to fetch positions for", connections.size)
            fetchesScheduledCounter.increment(connections.size.toDouble())

            // Process each connection with delays to avoid rate limiting
            runBlocking {
                connections.forEach { connection ->
                    try {
                        delay(1000) // 1 second between fetches

                        positionFetchService.executeScheduledFetch(
                            connectionId = connection.id,
                            userId = connection.user.id
                        )

                        fetchesSuccessCounter.increment()
                        log.debug("Completed scheduled fetch for connection {}", connection.id)

                    } catch (e: Exception) {
                        fetchesErrorCounter.increment()
                        log.error(
                            "Scheduled fetch failed for connection {} (user: {}, broker: {}): {}",
                            connection.id,
                            connection.user.id,
                            connection.broker.code,
                            e.message
                        )
                    }
                }
            }

            log.info("Completed scheduled position fetch for hour {} UTC", currentHourUtc)

        } catch (e: Exception) {
            log.error("Scheduled position fetch job failed: {}", e.message, e)
        }
    }

    /**
     * Manual trigger for testing or administrative purposes.
     */
    fun triggerManualScheduledFetch(hour: Int? = null) {
        val targetHour = hour ?: OffsetDateTime.now(ZoneOffset.UTC).hour
        log.info("Manually triggering scheduled fetch for hour {} UTC", targetHour)
        scheduledPositionFetch()
    }
}
