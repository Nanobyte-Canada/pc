package com.portfolio.broker.service

import com.portfolio.broker.dto.*
import com.portfolio.broker.entity.*
import com.portfolio.broker.repository.*
import com.portfolio.dto.request.PortfolioPositionRequest
import com.portfolio.service.IngestionInstrumentLookupService
import com.portfolio.service.LookThroughService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.OffsetDateTime

@Service
class ModelPortfolioService(
    private val modelRepository: ModelPortfolioRepository,
    private val allocationRepository: ModelPortfolioAllocationRepository,
    private val connectionRepository: BrokerConnectionRepository,
    private val instrumentLookup: IngestionInstrumentLookupService,
    private val lookThroughService: LookThroughService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun listAll(userId: Long): ModelPortfoliosListResponse {
        val models = modelRepository.findAllAccessible(userId)
        return ModelPortfoliosListResponse(models = models.map { it.toSummaryDto() })
    }

    fun getById(id: Long, userId: Long): ModelPortfolioDetailDto {
        val model = findAccessible(id, userId)
        return model.toDetailDto()
    }

    @Transactional
    fun create(userId: Long, request: CreateModelPortfolioRequest): ModelPortfolioDetailDto {
        val riskLevel = parseRiskLevel(request.riskLevel)
        validateAllocations(request.allocations)

        if (modelRepository.existsByUserIdAndName(userId, request.name)) {
            throw IllegalArgumentException("A model portfolio named '${request.name}' already exists")
        }

        val user = com.portfolio.auth.entity.User(id = userId, email = "", passwordHash = "", name = "")
        val model = ModelPortfolio(
            name = request.name,
            description = request.description,
            riskLevel = riskLevel,
            isSystem = false,
            user = user
        )
        val saved = modelRepository.save(model)

        request.allocations.forEach { input ->
            val alloc = ModelPortfolioAllocation(
                modelPortfolio = saved,
                symbol = input.symbol.uppercase(),
                targetPercent = input.targetPercent,
                assetClass = input.assetClass
            )
            allocationRepository.save(alloc)
            saved.allocations.add(alloc)
        }

        log.info("Created custom model portfolio '{}' (id={}) for user {}", saved.name, saved.id, userId)
        return saved.toDetailDto()
    }

    @Transactional
    fun update(id: Long, userId: Long, request: UpdateModelPortfolioRequest): ModelPortfolioDetailDto {
        val model = modelRepository.findByIdAndUserId(id, userId)
            ?: throw IllegalArgumentException("Model portfolio not found: $id")
        if (model.isSystem) {
            throw IllegalArgumentException("Cannot modify system model portfolios")
        }

        request.name?.let { newName ->
            if (newName != model.name && modelRepository.existsByUserIdAndName(userId, newName)) {
                throw IllegalArgumentException("A model portfolio named '$newName' already exists")
            }
            model.name = newName
        }
        request.description?.let { model.description = it }
        request.riskLevel?.let { model.riskLevel = parseRiskLevel(it) }
        model.updatedAt = OffsetDateTime.now()
        modelRepository.save(model)

        request.allocations?.let { newAllocations ->
            validateAllocations(newAllocations)
            allocationRepository.deleteByModelPortfolioId(id)
            model.allocations.clear()

            newAllocations.forEach { input ->
                val alloc = ModelPortfolioAllocation(
                    modelPortfolio = model,
                    symbol = input.symbol.uppercase(),
                    targetPercent = input.targetPercent,
                    assetClass = input.assetClass
                )
                allocationRepository.save(alloc)
                model.allocations.add(alloc)
            }
        }

        log.info("Updated model portfolio {} for user {}", id, userId)
        return model.toDetailDto()
    }

    @Transactional
    fun delete(id: Long, userId: Long) {
        val model = modelRepository.findByIdAndUserId(id, userId)
            ?: throw IllegalArgumentException("Model portfolio not found: $id")
        if (model.isSystem) {
            throw IllegalArgumentException("Cannot delete system model portfolios")
        }
        modelRepository.delete(model)
        log.info("Deleted model portfolio {} for user {}", id, userId)
    }

    @Transactional
    fun applyToAccounts(userId: Long, modelId: Long, connectionIds: List<Long>) {
        val model = findAccessible(modelId, userId)

        connectionIds.forEach { connId ->
            val connection = connectionRepository.findByIdAndUserId(connId, userId)
                ?: throw IllegalArgumentException("Connection not found: $connId")
            connection.modelPortfolio = model
            connectionRepository.save(connection)
        }

        log.info("Applied model '{}' (id={}) to {} accounts for user {}",
            model.name, modelId, connectionIds.size, userId)
    }

    fun getAnalysis(id: Long, userId: Long): ModelAnalysisDto {
        val model = findAccessible(id, userId)

        // Convert model allocations to PortfolioPositionRequest list via ingestion schema
        val allSymbols = model.allocations.map { it.symbol }.toSet()
        val instrumentsByTicker = instrumentLookup.findByTickers(allSymbols)

        val positions = model.allocations.mapNotNull { alloc ->
            val weight = alloc.targetPercent.divide(BigDecimal(100), 8, RoundingMode.HALF_UP).toDouble()
            if (weight <= 0) return@mapNotNull null

            val instrument = instrumentsByTicker[alloc.symbol.uppercase()] ?: return@mapNotNull null
            val type = when (instrument.instrumentType.uppercase()) {
                "ETF" -> "ETF"
                else -> "STOCK"
            }
            PortfolioPositionRequest(type, instrument.id, weight)
        }

        if (positions.isEmpty()) {
            return ModelAnalysisDto(
                modelId = model.id,
                sectorExposure = emptyList(),
                geographyExposure = emptyList(),
                riskScore = 0,
                riskLevel = "LOW",
                holdings = model.allocations.map { it.toDto() }
            )
        }

        val result = try {
            lookThroughService.computeLookThroughWithQuality(positions, LocalDate.now())
        } catch (e: Exception) {
            log.warn("Failed to compute model analysis for model {}", id, e)
            return ModelAnalysisDto(
                modelId = model.id,
                sectorExposure = emptyList(),
                geographyExposure = emptyList(),
                riskScore = 0,
                riskLevel = "LOW",
                holdings = model.allocations.map { it.toDto() }
            )
        }

        // Extract sector exposure
        val sectorMap = mutableMapOf<String, BigDecimal>()
        result.exposures.values.forEach { exposure ->
            val sectorCode = exposure.instrument.gicsSectorCode
            if (sectorCode != null) {
                val sectorName = LookThroughService.GICS_SECTOR_NAMES[sectorCode] ?: "Unknown"
                sectorMap.merge(sectorName, exposure.effectiveWeight) { a, b -> a.add(b) }
            }
        }
        // Include ETF direct sector exposures
        result.etfDirectSectorExposures.values.forEach { etfSector ->
            etfSector.sectorAllocations.forEach { (gicsCode, alloc) ->
                val effectiveWeight = etfSector.portfolioWeight * alloc
                val sectorName = LookThroughService.GICS_SECTOR_NAMES[gicsCode] ?: "Unknown"
                sectorMap.merge(sectorName, effectiveWeight) { a, b -> a.add(b) }
            }
        }
        val sectorExposure = sectorMap.entries
            .map { ExposureEntry(it.key, it.value.multiply(BigDecimal(100)).setScale(2, RoundingMode.HALF_UP)) }
            .sortedByDescending { it.percentage }

        // Extract geography exposure
        val geoMap = mutableMapOf<String, BigDecimal>()
        result.exposures.values.forEach { exposure ->
            val countryName = exposure.instrument.country ?: "Unknown"
            geoMap.merge(countryName, exposure.effectiveWeight) { a, b -> a.add(b) }
        }
        val geographyExposure = geoMap.entries
            .map { ExposureEntry(it.key, it.value.multiply(BigDecimal(100)).setScale(2, RoundingMode.HALF_UP)) }
            .sortedByDescending { it.percentage }

        // Calculate risk score based on sector concentration (HHI)
        val concentrationHHI = sectorExposure.sumOf { it.percentage.toDouble().let { p -> p * p } }
        val riskScore = (concentrationHHI / 100).toInt().coerceIn(0, 100)
        val riskLevel = when {
            riskScore < 30 -> "LOW"
            riskScore < 50 -> "MODERATE_LOW"
            riskScore < 70 -> "MODERATE"
            riskScore < 85 -> "MODERATE_HIGH"
            else -> "HIGH"
        }

        return ModelAnalysisDto(
            modelId = model.id,
            sectorExposure = sectorExposure,
            geographyExposure = geographyExposure,
            riskScore = riskScore,
            riskLevel = riskLevel,
            holdings = model.allocations.map { it.toDto() }
        )
    }

    private fun findAccessible(id: Long, userId: Long): ModelPortfolio {
        val model = modelRepository.findById(id).orElseThrow {
            IllegalArgumentException("Model portfolio not found: $id")
        }
        if (!model.isSystem && model.user?.id != userId) {
            throw IllegalArgumentException("Model portfolio not found: $id")
        }
        return model
    }

    private fun parseRiskLevel(level: String): RiskLevel {
        return try {
            RiskLevel.valueOf(level.uppercase())
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid risk level: $level. Must be one of: LOW, MODERATE, HIGH, EXTRA_HIGH")
        }
    }

    private fun validateAllocations(allocations: List<ModelAllocationInput>) {
        if (allocations.isEmpty()) {
            throw IllegalArgumentException("At least one allocation is required")
        }
        val total = allocations.sumOf { it.targetPercent }
        if (total > BigDecimal(100)) {
            throw IllegalArgumentException("Total allocation exceeds 100% (got $total%)")
        }
        allocations.forEach { a ->
            if (a.targetPercent <= BigDecimal.ZERO || a.targetPercent > BigDecimal(100)) {
                throw IllegalArgumentException("Allocation percent for ${a.symbol} must be between 0 and 100")
            }
        }
    }
}
