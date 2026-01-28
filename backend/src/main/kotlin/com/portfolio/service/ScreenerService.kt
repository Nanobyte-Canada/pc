package com.portfolio.service

import com.portfolio.dto.request.EtfFilterRequest
import com.portfolio.dto.request.MutualFundFilterRequest
import com.portfolio.dto.request.StockFilterRequest
import com.portfolio.dto.response.EtfDto
import com.portfolio.dto.response.MutualFundDto
import com.portfolio.dto.response.PagedResponseDto
import com.portfolio.dto.response.StockDto
import com.portfolio.entity.Etf
import com.portfolio.entity.MutualFund
import com.portfolio.entity.MutualFundStatus
import com.portfolio.entity.SecurityStatus
import com.portfolio.entity.Stock
import com.portfolio.repository.EtfRepository
import com.portfolio.repository.MutualFundRepository
import com.portfolio.repository.StockRepository
import jakarta.persistence.criteria.Predicate
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class ScreenerService(
    private val stockRepository: StockRepository,
    private val etfRepository: EtfRepository,
    private val mutualFundRepository: MutualFundRepository
) {
    fun filterStocks(filter: StockFilterRequest, pageable: Pageable): PagedResponseDto<StockDto> {
        val spec = buildStockSpecification(filter)
        val page: Page<Stock> = stockRepository.findAll(spec, pageable)
        return PagedResponseDto.from(page) { StockDto.from(it) }
    }

    fun filterEtfs(filter: EtfFilterRequest, pageable: Pageable): PagedResponseDto<EtfDto> {
        val spec = buildEtfSpecification(filter)
        val page: Page<Etf> = etfRepository.findAll(spec, pageable)
        return PagedResponseDto.from(page) { EtfDto.from(it) }
    }

    fun filterMutualFunds(filter: MutualFundFilterRequest, pageable: Pageable): PagedResponseDto<MutualFundDto> {
        val spec = buildMutualFundSpecification(filter)
        val page: Page<MutualFund> = mutualFundRepository.findAll(spec, pageable)
        return PagedResponseDto.from(page) { MutualFundDto.from(it) }
    }

    private fun buildStockSpecification(filter: StockFilterRequest): Specification<Stock> {
        return Specification { root, _, cb ->
            val predicates = mutableListOf<Predicate>()

            filter.sector?.let { sectorCode ->
                val subIndustry = root.join<Stock, Any>("gicsSubIndustry")
                val industry = subIndustry.join<Any, Any>("industry")
                val industryGroup = industry.join<Any, Any>("industryGroup")
                val sector = industryGroup.join<Any, Any>("sector")
                predicates.add(cb.equal(sector.get<String>("code"), sectorCode))
            }

            filter.country?.let {
                predicates.add(cb.equal(root.get<String>("country"), it))
            }

            filter.exchange?.let {
                predicates.add(cb.equal(root.get<String>("exchange"), it))
            }

            filter.status?.let {
                try {
                    val statusEnum = SecurityStatus.valueOf(it.uppercase())
                    predicates.add(cb.equal(root.get<SecurityStatus>("status"), statusEnum))
                } catch (_: IllegalArgumentException) {
                    // Invalid status, ignore filter
                }
            }

            filter.tickerContains?.let {
                predicates.add(cb.like(cb.upper(root.get("ticker")), "%${it.uppercase()}%"))
            }

            filter.nameContains?.let {
                predicates.add(cb.like(cb.upper(root.get("name")), "%${it.uppercase()}%"))
            }

            cb.and(*predicates.toTypedArray())
        }
    }

    private fun buildEtfSpecification(filter: EtfFilterRequest): Specification<Etf> {
        return Specification { root, _, cb ->
            val predicates = mutableListOf<Predicate>()

            filter.issuer?.let {
                predicates.add(cb.equal(root.get<String>("issuer"), it))
            }

            filter.assetClass?.let {
                predicates.add(cb.equal(root.get<String>("assetClass"), it))
            }

            filter.status?.let {
                try {
                    val statusEnum = SecurityStatus.valueOf(it.uppercase())
                    predicates.add(cb.equal(root.get<SecurityStatus>("status"), statusEnum))
                } catch (_: IllegalArgumentException) {
                    // Invalid status, ignore filter
                }
            }

            filter.symbolContains?.let {
                predicates.add(cb.like(cb.upper(root.get("symbol")), "%${it.uppercase()}%"))
            }

            filter.nameContains?.let {
                predicates.add(cb.like(cb.upper(root.get("name")), "%${it.uppercase()}%"))
            }

            filter.maxExpenseRatio?.let {
                predicates.add(cb.lessThanOrEqualTo(root.get("expenseRatio"), BigDecimal(it)))
            }

            cb.and(*predicates.toTypedArray())
        }
    }

    private fun buildMutualFundSpecification(filter: MutualFundFilterRequest): Specification<MutualFund> {
        return Specification { root, _, cb ->
            val predicates = mutableListOf<Predicate>()

            filter.issuer?.let {
                predicates.add(cb.equal(root.get<String>("issuer"), it))
            }

            filter.fundType?.let {
                predicates.add(cb.equal(root.get<String>("fundType"), it))
            }

            filter.assetClass?.let {
                predicates.add(cb.equal(root.get<String>("assetClass"), it))
            }

            filter.status?.let {
                try {
                    val statusEnum = MutualFundStatus.valueOf(it.uppercase())
                    predicates.add(cb.equal(root.get<MutualFundStatus>("status"), statusEnum))
                } catch (_: IllegalArgumentException) {
                    // Invalid status, ignore filter
                }
            }

            filter.symbolContains?.let {
                predicates.add(cb.like(cb.upper(root.get("symbol")), "%${it.uppercase()}%"))
            }

            filter.nameContains?.let {
                predicates.add(cb.like(cb.upper(root.get("name")), "%${it.uppercase()}%"))
            }

            filter.maxExpenseRatio?.let {
                predicates.add(cb.lessThanOrEqualTo(root.get("expenseRatio"), BigDecimal(it)))
            }

            filter.maxMinimumInvestment?.let {
                predicates.add(cb.lessThanOrEqualTo(root.get("minimumInvestment"), BigDecimal(it)))
            }

            cb.and(*predicates.toTypedArray())
        }
    }
}
