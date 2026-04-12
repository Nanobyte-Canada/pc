package com.portfolio.broker.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.portfolio.broker.dto.SnapshotDto
import com.portfolio.broker.dto.toDto
import com.portfolio.broker.entity.PortfolioGroup
import com.portfolio.broker.entity.PortfolioSnapshot
import com.portfolio.broker.repository.PortfolioGroupRepository
import com.portfolio.broker.repository.PortfolioSnapshotRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class PortfolioSnapshotService(
    private val snapshotRepository: PortfolioSnapshotRepository,
    private val groupRepository: PortfolioGroupRepository,
    private val driftCalculationService: DriftCalculationService,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun takeSnapshot(group: PortfolioGroup, date: LocalDate = LocalDate.now()): SnapshotDto {
        if (snapshotRepository.existsByGroupIdAndSnapshotDate(group.id, date)) {
            log.info("Snapshot already exists for group {} on {}", group.id, date)
            val existing = snapshotRepository.findByGroupIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
                group.id, date, date
            ).first()
            return existing.toDto()
        }

        val drift = driftCalculationService.calculateDrift(group.id)

        // Serialize positions as JSON
        val positionsJson = objectMapper.writeValueAsString(
            drift.holdings.map { h ->
                mapOf(
                    "symbol" to h.symbol,
                    "securityName" to h.securityName,
                    "actualValue" to h.actualValue,
                    "actualPercent" to h.actualPercent,
                    "currency" to h.currency
                )
            }
        )

        val cashJson = objectMapper.writeValueAsString(drift.cash)

        val snapshot = PortfolioSnapshot(
            group = group,
            snapshotDate = date,
            totalValue = drift.totalValue,
            positions = positionsJson,
            cash = cashJson,
            accuracy = drift.accuracy
        )

        val saved = snapshotRepository.save(snapshot)
        log.info("Snapshot taken for group {} on {}: value={}", group.id, date, drift.totalValue)
        return saved.toDto()
    }

    @Transactional
    fun takeSnapshotsForAllGroups(date: LocalDate = LocalDate.now()) {
        val groups = groupRepository.findAll()
        var count = 0
        for (group in groups) {
            try {
                if (!snapshotRepository.existsByGroupIdAndSnapshotDate(group.id, date)) {
                    takeSnapshot(group, date)
                    count++
                }
            } catch (e: Exception) {
                log.warn("Failed to take snapshot for group {}: {}", group.id, e.message)
            }
        }
        log.info("Took {} snapshots for {} groups on {}", count, groups.size, date)
    }

    fun getSnapshots(groupId: Long, startDate: LocalDate, endDate: LocalDate): List<SnapshotDto> {
        return snapshotRepository.findByGroupIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
            groupId, startDate, endDate
        ).map { it.toDto() }
    }
}
