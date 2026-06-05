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

@Component
@EnableScheduling
class TokenRefreshScheduler(
    private val connectionRepository: GatewayConnectionRepository,
    private val credentialService: CredentialService,
    private val adapterRegistry: AdapterRegistry
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedRate = 5 * 60 * 1000, initialDelay = 60 * 1000)
    @Transactional
    fun refreshExpiringTokens() {
        val connections = connectionRepository.findByStatusIn(listOf("ACTIVE", "ERROR"))
        if (connections.isEmpty()) return

        log.info("Token refresh scheduler: checking {} connections", connections.size)
        var refreshed = 0
        var recovered = 0
        var failed = 0

        for (conn in connections) {
            try {
                val credentials = credentialService.getCredentials(conn.id)
                val adapter = adapterRegistry.getAdapter(credentials.brokerType)
                val updated = credentialService.getCredentialsWithRefresh(conn.id, adapter)

                try {
                    adapter.validateConnection(updated)
                } catch (authEx: BrokerAuthenticationException) {
                    log.warn("Token valid by timestamp but rejected by broker for {}, force-refreshing", conn.id)
                    credentialService.forceRefresh(conn.id, adapter)
                }

                refreshed++
                if (conn.status == "ERROR") {
                    conn.status = "ACTIVE"
                    conn.errorMessage = null
                    recovered++
                    log.info("Connection {} recovered from ERROR to ACTIVE", conn.id)
                }
                conn.refreshFailureCount = 0
                connectionRepository.save(conn)
            } catch (e: Exception) {
                conn.refreshFailureCount++
                failed++

                if (conn.refreshFailureCount >= 3 && conn.status != "ERROR") {
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

        if (refreshed > 0 || recovered > 0 || failed > 0) {
            log.info("Token refresh complete: {} refreshed, {} recovered, {} failed", refreshed, recovered, failed)
        }
    }
}
