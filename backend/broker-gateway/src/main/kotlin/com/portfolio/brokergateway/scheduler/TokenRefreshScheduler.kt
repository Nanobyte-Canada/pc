package com.portfolio.brokergateway.scheduler

import com.portfolio.brokergateway.config.AdapterRegistry
import com.portfolio.brokergateway.credential.CredentialService
import com.portfolio.brokergateway.credential.GatewayConnectionRepository
import com.portfolio.brokergateway.exception.BrokerAuthenticationException
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentHashMap

@Component
@EnableScheduling
class TokenRefreshScheduler(
    private val connectionRepository: GatewayConnectionRepository,
    private val credentialService: CredentialService,
    private val adapterRegistry: AdapterRegistry
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // In-memory backoff; fine for the single-replica deployment. Multi-replica would need ShedLock.
    private val backoffUntil = ConcurrentHashMap<String, Instant>()

    @Scheduled(fixedRate = 5 * 60 * 1000, initialDelay = 60 * 1000)
    @Transactional
    fun refreshExpiringTokens() {
        val connections = connectionRepository.findByStatusIn(listOf("ACTIVE", "ERROR"))
        if (connections.isEmpty()) return

        log.info("Token refresh scheduler: checking {} connections", connections.size)
        var refreshed = 0
        var recovered = 0
        var failed = 0
        var skipped = 0

        val now = Instant.now()
        for (conn in connections) {
            if (backoffUntil[conn.id]?.isAfter(now) == true) {
                skipped++   // failure backoff
                continue
            }
            try {
                val credentials = credentialService.getCredentials(conn.id)
                val adapter = adapterRegistry.getAdapter(credentials.brokerType)
                // cheap: refreshes only when the access token is within 300s of expiry; no broker call otherwise
                val updated = credentialService.getCredentialsWithRefresh(conn.id, adapter)

                // full broker validation only when needed: ERROR status, never validated, or stale (>24h)
                val lastValidated = conn.lastValidatedAt
                val stale = lastValidated == null ||
                    lastValidated.isBefore(OffsetDateTime.now().minusHours(24))
                if (conn.status == "ERROR" || stale) {
                    val validation = adapter.validateConnection(updated)
                    if (!validation.connected) {
                        log.warn("Token valid by timestamp but rejected by broker for {}, force-refreshing", conn.id)
                        val forceRefreshed = credentialService.forceRefresh(conn.id, adapter)
                        val retry = adapter.validateConnection(forceRefreshed)
                        if (!retry.connected) {
                            throw BrokerAuthenticationException(
                                "Validation failed after force-refresh: ${retry.message}", credentials.brokerType)
                        }
                    }
                    conn.lastValidatedAt = OffsetDateTime.now()   // only writer of lastValidatedAt: without this stamp the staleness check above would re-validate on every run
                }

                if (conn.status == "ERROR") {
                    conn.status = "ACTIVE"
                    conn.errorMessage = null
                    recovered++
                    log.info("Connection {} recovered from ERROR to ACTIVE", conn.id)
                }
                conn.refreshFailureCount = 0
                backoffUntil.remove(conn.id)
                connectionRepository.save(conn)
                refreshed++
            } catch (e: Exception) {
                conn.refreshFailureCount++
                failed++
                // 5/10/20/40/60 min: 5 min × 2^(failures−1), capped at 60 min (300s shl n, capped 3600s)
                val backoffSeconds = minOf(300L shl minOf(conn.refreshFailureCount - 1, 4), 3600L)
                backoffUntil[conn.id] = now.plusSeconds(backoffSeconds)

                if (conn.refreshFailureCount >= 10) {
                    conn.status = "EXPIRED"
                    conn.errorMessage = "Connection expired — please reconnect your broker"
                    log.error("Connection {} marked EXPIRED after {} consecutive failures, stopping retries",
                        conn.id, conn.refreshFailureCount)
                } else if (conn.refreshFailureCount >= 3 && conn.status != "ERROR") {
                    conn.status = "ERROR"
                    conn.errorMessage = "Token refresh failed after ${conn.refreshFailureCount} attempts: ${e.message}"
                    log.error("Connection {} marked ERROR after {} consecutive failures", conn.id, conn.refreshFailureCount)
                } else {
                    log.warn("Token refresh failed for connection {} (attempt {}): {}",
                        conn.id, conn.refreshFailureCount, e.message)
                }
                connectionRepository.save(conn)
            }
        }

        if (refreshed > 0 || recovered > 0 || failed > 0 || skipped > 0) {
            log.info("Token refresh complete: {} refreshed, {} recovered, {} failed, {} skipped",
                refreshed, recovered, failed, skipped)
        }
    }
}
