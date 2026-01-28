package com.portfolio.service

import com.portfolio.dto.response.AvailableDatesResponseDto
import com.portfolio.dto.response.EtfHoldingsResponseDto
import com.portfolio.dto.response.MutualFundHoldingsResponseDto
import com.portfolio.dto.response.toHoldingDto
import com.portfolio.dto.response.toMetadata
import com.portfolio.dto.response.toMutualFundMetadata
import com.portfolio.repository.EtfHoldingRepository
import com.portfolio.repository.EtfRepository
import com.portfolio.repository.MutualFundHoldingRepository
import com.portfolio.repository.MutualFundRepository
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class HoldingsService(
    private val etfRepository: EtfRepository,
    private val mutualFundRepository: MutualFundRepository,
    private val etfHoldingRepository: EtfHoldingRepository,
    private val mutualFundHoldingRepository: MutualFundHoldingRepository
) {
    fun getEtfHoldings(etfId: Long, asOfDate: LocalDate?, includeMetadata: Boolean = true): EtfHoldingsResponseDto? {
        val etf = etfRepository.findById(etfId).orElse(null) ?: return null

        val holdings = if (asOfDate != null) {
            etfHoldingRepository.findLatestHoldingsIncludingUnresolved(etfId, asOfDate)
        } else {
            val latestDate = etfHoldingRepository.findDistinctAsOfDatesByEtfId(etfId).firstOrNull()
            if (latestDate != null) {
                etfHoldingRepository.findLatestHoldingsIncludingUnresolved(etfId, latestDate)
            } else {
                emptyList()
            }
        }

        val effectiveDate = holdings.firstOrNull()?.asOfDate ?: asOfDate ?: LocalDate.now()

        return EtfHoldingsResponseDto(
            etfId = etf.id,
            etfSymbol = etf.symbol,
            asOfDate = effectiveDate,
            holdingsCount = holdings.size,
            holdings = holdings.map { it.toHoldingDto() },
            metadata = if (includeMetadata) holdings.toMetadata() else null
        )
    }

    fun getEtfHoldingsDates(etfId: Long): AvailableDatesResponseDto {
        val dates = etfHoldingRepository.findDistinctAsOfDatesByEtfId(etfId)
        return AvailableDatesResponseDto(dates)
    }

    fun getMutualFundHoldings(fundId: Long, asOfDate: LocalDate?, includeMetadata: Boolean = true): MutualFundHoldingsResponseDto? {
        val fund = mutualFundRepository.findById(fundId).orElse(null) ?: return null

        val holdings = if (asOfDate != null) {
            mutualFundHoldingRepository.findLatestHoldingsIncludingUnresolved(fundId, asOfDate)
        } else {
            val latestDate = mutualFundHoldingRepository.findDistinctAsOfDatesByMutualFundId(fundId).firstOrNull()
            if (latestDate != null) {
                mutualFundHoldingRepository.findLatestHoldingsIncludingUnresolved(fundId, latestDate)
            } else {
                emptyList()
            }
        }

        val effectiveDate = holdings.firstOrNull()?.asOfDate ?: asOfDate ?: LocalDate.now()

        return MutualFundHoldingsResponseDto(
            mutualFundId = fund.id,
            fundSymbol = fund.symbol,
            asOfDate = effectiveDate,
            holdingsCount = holdings.size,
            holdings = holdings.map { it.toHoldingDto() },
            metadata = if (includeMetadata) holdings.toMutualFundMetadata() else null
        )
    }

    fun getMutualFundHoldingsDates(fundId: Long): AvailableDatesResponseDto {
        val dates = mutualFundHoldingRepository.findDistinctAsOfDatesByMutualFundId(fundId)
        return AvailableDatesResponseDto(dates)
    }
}
