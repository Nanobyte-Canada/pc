package com.portfolio.auth.scheduler

import com.portfolio.auth.repository.OAuthStateRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Daily cleanup of the oauth_states table (marker: OAUTH_STATE_CLEANUP).
 *
 * Deletes rows that are expired or already consumed. The repository query
 * [OAuthStateRepository.deleteExpiredAndUsed] previously had no production caller,
 * so expired/used state rows accumulated indefinitely.
 *
 * Requires @EnableScheduling, which is already active on com.portfolio.Application.
 */
@Component
class OAuthStateCleanupScheduler(
    private val oauthStateRepository: OAuthStateRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Runs daily at 04:00 server time by default; overridable via
     * `auth.oauth-state.cleanup.cron` (standard Spring cron expression).
     */
    @Transactional
    @Scheduled(cron = "\${auth.oauth-state.cleanup.cron:0 0 4 * * *}")
    fun deleteExpiredAndUsedStates() {
        try {
            oauthStateRepository.deleteExpiredAndUsed()
            log.info("OAUTH_STATE_CLEANUP: removed expired and used OAuth state rows")
        } catch (e: Exception) {
            log.warn("OAUTH_STATE_CLEANUP failed: {}", e.message)
        }
    }
}
