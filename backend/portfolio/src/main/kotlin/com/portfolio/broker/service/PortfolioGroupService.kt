package com.portfolio.broker.service

import com.portfolio.broker.dto.*
import com.portfolio.broker.entity.*
import com.portfolio.broker.repository.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

@Service
class PortfolioGroupService(
    private val groupRepository: PortfolioGroupRepository,
    private val targetRepository: PortfolioTargetRepository,
    private val groupAccountRepository: PortfolioGroupAccountRepository,
    private val settingsRepository: PortfolioGroupSettingsRepository,
    private val excludedAssetRepository: PortfolioExcludedAssetRepository,
    private val connectionRepository: BrokerConnectionRepository,
    private val driftCalculationService: DriftCalculationService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // ========== Group CRUD ==========

    @Transactional
    fun createGroup(userId: Long, request: CreatePortfolioGroupRequest): PortfolioGroupDetailDto {
        if (groupRepository.existsByUserIdAndName(userId, request.name)) {
            throw IllegalArgumentException("A portfolio group named '${request.name}' already exists")
        }

        val user = com.portfolio.auth.entity.User(id = userId, email = "", passwordHash = "", name = "")
        val group = PortfolioGroup(
            user = user,
            name = request.name,
            description = request.description
        )
        val savedGroup = groupRepository.save(group)

        // Create default settings
        val settings = PortfolioGroupSettings(group = savedGroup)
        settingsRepository.save(settings)
        savedGroup.settings = settings

        // Add targets if provided
        request.targets?.let { targets ->
            validateTargetTotal(targets)
            targets.forEach { input ->
                val target = PortfolioTarget(
                    group = savedGroup,
                    symbol = input.symbol.uppercase(),
                    targetPercent = input.targetPercent
                )
                targetRepository.save(target)
                savedGroup.targets.add(target)
            }
        }

        // Link accounts if provided
        request.accountIds?.forEach { connectionId ->
            linkAccountInternal(savedGroup, connectionId, userId)
        }

        log.info("Created portfolio group '{}' (id={}) for user {}", savedGroup.name, savedGroup.id, userId)
        return buildDetailDto(savedGroup)
    }

    fun getGroup(groupId: Long, userId: Long): PortfolioGroupDetailDto {
        val group = getGroupEntity(groupId, userId)
        return buildDetailDto(group)
    }

    fun listGroups(userId: Long): PortfolioGroupsListResponse {
        val groups = groupRepository.findByUserId(userId)
        val summaries = groups.map { group ->
            val accuracy = try {
                driftCalculationService.calculateAccuracy(group.id)
            } catch (e: Exception) {
                BigDecimal.ZERO
            }
            val totalValue = try {
                driftCalculationService.calculateTotalValue(group.id)
            } catch (e: Exception) {
                BigDecimal.ZERO
            }
            PortfolioGroupSummaryDto(
                id = group.id,
                name = group.name,
                description = group.description,
                accountCount = group.linkedAccounts.size,
                targetCount = group.targets.size,
                totalValue = totalValue,
                accuracy = accuracy
            )
        }
        return PortfolioGroupsListResponse(groups = summaries)
    }

    @Transactional
    fun updateGroup(groupId: Long, userId: Long, request: UpdatePortfolioGroupRequest): PortfolioGroupDetailDto {
        val group = getGroupEntity(groupId, userId)

        request.name?.let { newName ->
            if (newName != group.name && groupRepository.existsByUserIdAndName(userId, newName)) {
                throw IllegalArgumentException("A portfolio group named '$newName' already exists")
            }
            group.name = newName
        }
        request.description?.let { group.description = it }

        groupRepository.save(group)
        log.info("Updated portfolio group {} for user {}", groupId, userId)
        return buildDetailDto(group)
    }

    @Transactional
    fun deleteGroup(groupId: Long, userId: Long) {
        val group = getGroupEntity(groupId, userId)
        groupRepository.delete(group)
        log.info("Deleted portfolio group {} for user {}", groupId, userId)
    }

    // ========== Targets ==========

    @Transactional
    fun setTargets(groupId: Long, userId: Long, request: SetTargetsRequest): List<TargetAllocationDto> {
        val group = getGroupEntity(groupId, userId)
        validateTargetTotal(request.targets)

        targetRepository.deleteByGroupId(groupId)
        group.targets.clear()

        val saved = request.targets.map { input ->
            val target = PortfolioTarget(
                group = group,
                symbol = input.symbol.uppercase(),
                targetPercent = input.targetPercent
            )
            targetRepository.save(target)
        }

        log.info("Set {} targets for portfolio group {} (user {})", saved.size, groupId, userId)
        return saved.map { it.toDto() }
    }

    @Transactional
    fun addTarget(groupId: Long, userId: Long, input: TargetInput): TargetAllocationDto {
        val group = getGroupEntity(groupId, userId)
        val symbol = input.symbol.uppercase()

        if (targetRepository.findByGroupIdAndSymbol(groupId, symbol) != null) {
            throw IllegalArgumentException("Target for symbol '$symbol' already exists in this group")
        }

        val existingTotal = group.targets.sumOf { it.targetPercent }
        val newTotal = existingTotal + input.targetPercent
        if (newTotal > BigDecimal(100)) {
            throw IllegalArgumentException("Total target allocation would exceed 100% (current: $existingTotal%, adding: ${input.targetPercent}%)")
        }

        val target = PortfolioTarget(
            group = group,
            symbol = symbol,
            targetPercent = input.targetPercent
        )
        val saved = targetRepository.save(target)
        log.info("Added target {} ({}%) to portfolio group {}", symbol, input.targetPercent, groupId)
        return saved.toDto()
    }

    @Transactional
    fun removeTarget(groupId: Long, userId: Long, symbol: String) {
        getGroupEntity(groupId, userId)
        val target = targetRepository.findByGroupIdAndSymbol(groupId, symbol.uppercase())
            ?: throw IllegalArgumentException("Target for symbol '$symbol' not found in this group")
        targetRepository.delete(target)
        log.info("Removed target {} from portfolio group {}", symbol, groupId)
    }

    // ========== Account Linking ==========

    @Transactional
    fun linkAccount(groupId: Long, userId: Long, connectionId: Long): LinkedAccountDto {
        val group = getGroupEntity(groupId, userId)
        val link = linkAccountInternal(group, connectionId, userId)
        return link.toLinkedAccountDto()
    }

    @Transactional
    fun unlinkAccount(groupId: Long, userId: Long, connectionId: Long) {
        getGroupEntity(groupId, userId)
        val link = groupAccountRepository.findByGroupIdAndConnectionId(groupId, connectionId)
            ?: throw IllegalArgumentException("Account $connectionId is not linked to this group")
        groupAccountRepository.delete(link)
        log.info("Unlinked account {} from portfolio group {}", connectionId, groupId)
    }

    // ========== Settings ==========

    fun getSettings(groupId: Long, userId: Long): PortfolioGroupSettingsDto {
        getGroupEntity(groupId, userId)
        val settings = settingsRepository.findByGroupId(groupId)
        return settings?.toDto() ?: DEFAULT_SETTINGS_DTO
    }

    @Transactional
    fun updateSettings(groupId: Long, userId: Long, request: UpdateSettingsRequest): PortfolioGroupSettingsDto {
        val group = getGroupEntity(groupId, userId)
        val settings = settingsRepository.findByGroupId(groupId) ?: PortfolioGroupSettings(group = group)

        request.sellToRebalance?.let { settings.sellToRebalance = it }
        request.keepCurrenciesSeparate?.let { settings.keepCurrenciesSeparate = it }
        request.preventNonTradableTrades?.let { settings.preventNonTradableTrades = it }
        request.notifyNewAssets?.let { settings.notifyNewAssets = it }
        request.retainCashForExchange?.let { settings.retainCashForExchange = it }
        request.rebalanceFrequency?.let {
            val freq = try { RebalanceFrequency.valueOf(it.uppercase()) } catch (e: Exception) {
                throw IllegalArgumentException("Invalid rebalance frequency: $it")
            }
            settings.rebalanceFrequency = freq
            // Compute next rebalance date when frequency changes
            if (freq != RebalanceFrequency.MANUAL) {
                settings.nextRebalanceDate = computeNextRebalanceDate(freq, LocalDate.now())
            } else {
                settings.nextRebalanceDate = null
            }
        }
        request.accuracyThreshold?.let { settings.accuracyThreshold = it }
        request.autoExecute?.let { settings.autoExecute = it }
        settings.updatedAt = OffsetDateTime.now()

        settingsRepository.save(settings)
        log.info("Updated settings for portfolio group {}", groupId)
        return settings.toDto()
    }

    // ========== Excluded Assets ==========

    fun getExcludedAssets(groupId: Long, userId: Long): List<ExcludedAssetDto> {
        getGroupEntity(groupId, userId)
        return excludedAssetRepository.findByGroupId(groupId).map { it.toDto() }
    }

    @Transactional
    fun addExcludedAsset(groupId: Long, userId: Long, symbol: String): ExcludedAssetDto {
        val group = getGroupEntity(groupId, userId)
        val upperSymbol = symbol.uppercase()

        if (excludedAssetRepository.existsByGroupIdAndSymbol(groupId, upperSymbol)) {
            throw IllegalArgumentException("Asset '$upperSymbol' is already excluded")
        }

        val excluded = PortfolioExcludedAsset(group = group, symbol = upperSymbol)
        excludedAssetRepository.save(excluded)
        log.info("Added excluded asset {} to portfolio group {}", upperSymbol, groupId)
        return excluded.toDto()
    }

    @Transactional
    fun removeExcludedAsset(groupId: Long, userId: Long, symbol: String) {
        getGroupEntity(groupId, userId)
        val excluded = excludedAssetRepository.findByGroupIdAndSymbol(groupId, symbol.uppercase())
            ?: throw IllegalArgumentException("Asset '$symbol' is not in the excluded list")
        excludedAssetRepository.delete(excluded)
        log.info("Removed excluded asset {} from portfolio group {}", symbol, groupId)
    }

    // ========== Internal Helpers ==========

    fun getGroupEntity(groupId: Long, userId: Long): PortfolioGroup {
        return groupRepository.findByIdAndUserId(groupId, userId)
            ?: throw IllegalArgumentException("Portfolio group not found: $groupId")
    }

    private fun linkAccountInternal(group: PortfolioGroup, connectionId: Long, userId: Long): PortfolioGroupAccount {
        val connection = connectionRepository.findByIdAndUserId(connectionId, userId)
            ?: throw IllegalArgumentException("Connection not found: $connectionId")

        if (!connection.isActive()) {
            throw IllegalArgumentException("Connection $connectionId is not active (status: ${connection.status})")
        }

        if (groupAccountRepository.existsByGroupIdAndConnectionId(group.id, connectionId)) {
            throw IllegalArgumentException("Account $connectionId is already linked to this group")
        }

        val link = PortfolioGroupAccount(group = group, connection = connection)
        val saved = groupAccountRepository.save(link)
        group.linkedAccounts.add(saved)
        log.info("Linked account {} to portfolio group {}", connectionId, group.id)
        return saved
    }

    companion object {
        fun computeNextRebalanceDate(frequency: RebalanceFrequency, from: LocalDate): LocalDate {
            return when (frequency) {
                RebalanceFrequency.MONTHLY -> from.plusMonths(1).withDayOfMonth(1)
                RebalanceFrequency.QUARTERLY -> {
                    val nextQuarterMonth = ((from.monthValue - 1) / 3 + 1) * 3 + 1
                    if (nextQuarterMonth > 12) from.plusYears(1).withMonth(1).withDayOfMonth(1)
                    else from.withMonth(nextQuarterMonth).withDayOfMonth(1)
                }
                RebalanceFrequency.SEMI_ANNUALLY -> from.plusMonths(6).withDayOfMonth(1)
                RebalanceFrequency.ANNUALLY -> from.plusYears(1).withMonth(1).withDayOfMonth(1)
                RebalanceFrequency.MANUAL -> from
            }
        }
    }

    private fun validateTargetTotal(targets: List<TargetInput>) {
        val total = targets.sumOf { it.targetPercent }
        if (total > BigDecimal(100)) {
            throw IllegalArgumentException("Total target allocation exceeds 100% (got $total%)")
        }
        targets.forEach { t ->
            if (t.targetPercent < BigDecimal.ZERO || t.targetPercent > BigDecimal(100)) {
                throw IllegalArgumentException("Target percent for ${t.symbol} must be between 0 and 100")
            }
        }
    }

    private fun buildDetailDto(group: PortfolioGroup): PortfolioGroupDetailDto {
        val accuracy = try {
            driftCalculationService.calculateAccuracy(group.id)
        } catch (e: Exception) {
            BigDecimal.ZERO
        }
        val totalValue = try {
            driftCalculationService.calculateTotalValue(group.id)
        } catch (e: Exception) {
            BigDecimal.ZERO
        }

        return PortfolioGroupDetailDto(
            id = group.id,
            name = group.name,
            description = group.description,
            targets = group.targets.map { it.toDto() },
            linkedAccounts = group.linkedAccounts.map { it.toLinkedAccountDto() },
            settings = group.settings?.toDto() ?: DEFAULT_SETTINGS_DTO,
            excludedAssets = group.excludedAssets.map { it.toDto() },
            totalValue = totalValue,
            accuracy = accuracy
        )
    }
}
