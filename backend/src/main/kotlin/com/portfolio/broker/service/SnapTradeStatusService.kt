package com.portfolio.broker.service

import com.portfolio.broker.config.SnapTradeConfig
import com.portfolio.broker.dto.SnapTradeStatusDto
import com.portfolio.broker.entity.SnapTradeApiStatus
import com.portfolio.broker.entity.SnapTradeStatusCheck
import com.portfolio.broker.repository.SnapTradeStatusRepository
import com.snaptrade.client.Configuration
import com.snaptrade.client.Snaptrade
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import kotlin.system.measureTimeMillis

@Service
class SnapTradeStatusService(
    private val statusRepository: SnapTradeStatusRepository,
    private val config: SnapTradeConfig
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val snaptrade: Snaptrade by lazy {
        val configuration = Configuration()
        configuration.clientId = config.clientId
        configuration.consumerKey = config.consumerKey
        Snaptrade(configuration)
    }

    fun checkAndStoreStatus(): SnapTradeStatusCheck {
        var status: SnapTradeApiStatus
        var responseTimeMs: Int
        var version: String? = null
        var errorMessage: String? = null
        var rawResponse: String? = null

        try {
            val result: Any?
            val elapsed = measureTimeMillis {
                result = snaptrade.apiStatus.check().execute()
            }
            responseTimeMs = elapsed.toInt()

            // Parse the response
            when (result) {
                is Map<*, *> -> {
                    val online = result["online"] as? Boolean ?: false
                    version = result["version"] as? String
                    status = if (online) SnapTradeApiStatus.ONLINE else SnapTradeApiStatus.DEGRADED
                    rawResponse = result.toString()
                }
                else -> {
                    status = SnapTradeApiStatus.UNKNOWN
                    rawResponse = result?.toString()
                }
            }
        } catch (e: Exception) {
            log.error("SnapTrade health check failed: {}", e.message)
            status = SnapTradeApiStatus.OFFLINE
            responseTimeMs = 0
            errorMessage = e.message
        }

        val check = SnapTradeStatusCheck(
            status = status,
            responseTimeMs = responseTimeMs,
            version = version,
            errorMessage = errorMessage,
            rawResponse = rawResponse
        )

        val saved = statusRepository.save(check)
        log.info("SnapTrade health check: status={}, responseTime={}ms, version={}", status, responseTimeMs, version)
        return saved
    }

    fun getLatestStatus(): SnapTradeStatusDto? {
        val latest = statusRepository.findLatest() ?: return null
        val uptime = getUptimePercentage()
        return latest.toDto(uptime)
    }

    fun getUptimePercentage(hours: Int = 24): Double {
        val since = OffsetDateTime.now().minusHours(hours.toLong())
        val total = statusRepository.countChecksSince(since)
        if (total == 0L) return 100.0

        val online = statusRepository.countByStatusSince(SnapTradeApiStatus.ONLINE, since)
        return (online.toDouble() / total.toDouble()) * 100.0
    }

    private fun SnapTradeStatusCheck.toDto(uptimePercent: Double) = SnapTradeStatusDto(
        status = status.name,
        responseTimeMs = responseTimeMs,
        version = version,
        uptimePercent24h = uptimePercent,
        lastChecked = checkedAt
    )
}
