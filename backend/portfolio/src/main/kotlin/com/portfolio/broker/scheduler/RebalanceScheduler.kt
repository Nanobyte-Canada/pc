package com.portfolio.broker.scheduler

import com.portfolio.broker.entity.*
import com.portfolio.broker.repository.PortfolioGroupRepository
import com.portfolio.broker.repository.PortfolioGroupSettingsRepository
import com.portfolio.broker.repository.RebalanceEventRepository
import com.portfolio.broker.service.DriftCalculationService
import com.portfolio.broker.service.NotificationService
import com.portfolio.broker.service.PortfolioGroupService
import com.portfolio.broker.service.RebalanceService
import com.portfolio.broker.service.OrderExecutionService
import com.portfolio.broker.dto.ExecuteTradesRequest
import com.portfolio.broker.dto.TradeExecutionInput
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

@Component
@ConditionalOnProperty(
    prefix = "broker.sync",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class RebalanceScheduler(
    private val groupRepository: PortfolioGroupRepository,
    private val settingsRepository: PortfolioGroupSettingsRepository,
    private val rebalanceEventRepository: RebalanceEventRepository,
    private val driftCalculationService: DriftCalculationService,
    private val rebalanceService: RebalanceService,
    private val notificationService: NotificationService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Runs daily at 7 AM ET. Checks each portfolio group for:
     * 1. Scheduled rebalance (next_rebalance_date <= today)
     * 2. Accuracy-based trigger (accuracy < threshold)
     */
    @Scheduled(cron = "\${rebalance.check.cron:0 0 7 * * *}")
    fun checkRebalanceNeeded() {
        log.info("Starting daily rebalance check")

        val allSettings = settingsRepository.findAll()
        val today = LocalDate.now()
        var checked = 0
        var triggered = 0

        for (settings in allSettings) {
            if (settings.rebalanceFrequency == RebalanceFrequency.MANUAL &&
                settings.accuracyThreshold >= BigDecimal(100)) {
                continue // No automation configured
            }

            try {
                val groupId = settings.group.id
                val result = checkGroup(settings, today)
                checked++
                if (result) triggered++
            } catch (e: Exception) {
                log.warn("Rebalance check failed for group {}: {}", settings.group.id, e.message)
            }
        }

        log.info("Rebalance check complete: checked {} groups, triggered {} actions", checked, triggered)
    }

    @Transactional
    fun checkGroup(settings: PortfolioGroupSettings, today: LocalDate): Boolean {
        val group = settings.group
        val groupId = group.id

        // Check if group has targets and accounts
        if (group.targets.isEmpty() || group.linkedAccounts.isEmpty()) return false

        val accuracy = try {
            driftCalculationService.calculateAccuracy(groupId)
        } catch (e: Exception) {
            log.debug("Cannot calculate accuracy for group {}: {}", groupId, e.message)
            return false
        }

        // Determine trigger
        var triggerType: RebalanceTriggerType? = null
        var notes: String? = null

        // Check scheduled rebalance
        if (settings.rebalanceFrequency != RebalanceFrequency.MANUAL &&
            settings.nextRebalanceDate != null &&
            !settings.nextRebalanceDate!!.isAfter(today)) {
            triggerType = RebalanceTriggerType.SCHEDULED
            notes = "Scheduled ${settings.rebalanceFrequency.name.lowercase()} rebalance"
        }

        // Check accuracy threshold
        if (triggerType == null && accuracy < settings.accuracyThreshold) {
            triggerType = RebalanceTriggerType.ACCURACY_DROP
            notes = "Accuracy ${accuracy}% below threshold ${settings.accuracyThreshold}%"
        }

        if (triggerType == null) return false

        log.info("Rebalance triggered for group {} ({}): {}", groupId, group.name, notes)

        // Create notification
        notificationService.createNotification(
            user = group.user,
            type = NotificationType.REBALANCE_REMINDER,
            title = "Rebalance Needed: ${group.name}",
            message = notes ?: "Your portfolio needs rebalancing",
            link = "/portfolios/$groupId"
        )

        // Record the event
        val event = RebalanceEvent(
            group = group,
            triggerType = triggerType,
            accuracyBefore = accuracy,
            status = RebalanceStatus.PENDING_APPROVAL,
            notes = notes
        )
        rebalanceEventRepository.save(event)

        // Update next rebalance date if this was a scheduled trigger
        if (triggerType == RebalanceTriggerType.SCHEDULED) {
            settings.nextRebalanceDate = PortfolioGroupService.computeNextRebalanceDate(
                settings.rebalanceFrequency, today
            )
            settingsRepository.save(settings)
        }

        return true
    }
}
