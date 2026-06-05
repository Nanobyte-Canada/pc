package com.portfolio.marketdata.db.repository

import com.portfolio.marketdata.db.entity.ContractCacheEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate

@Repository
interface ContractCacheRepository : JpaRepository<ContractCacheEntity, Long> {
    fun findBySymbolAndSecTypeAndExpiryAndStrikeAndOptionRight(
        symbol: String, secType: String, expiry: LocalDate?, strike: BigDecimal?, optionRight: String?
    ): ContractCacheEntity?
}
