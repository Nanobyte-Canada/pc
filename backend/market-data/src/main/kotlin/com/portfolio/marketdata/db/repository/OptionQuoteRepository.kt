package com.portfolio.marketdata.db.repository

import com.portfolio.marketdata.db.entity.OptionQuoteEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@Repository
interface OptionQuoteRepository : JpaRepository<OptionQuoteEntity, Long> {
    fun findByTickerAndExpiryAndStrikeAndOptionType(
        ticker: String, expiry: LocalDate, strike: BigDecimal, optionType: String
    ): List<OptionQuoteEntity>

    fun findByTickerAndObservedAtAfter(ticker: String, after: Instant): List<OptionQuoteEntity>
}
