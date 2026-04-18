package com.portfolio.marketdata.db.repository

import com.portfolio.marketdata.db.entity.UnderlyingPriceEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UnderlyingPriceRepository : JpaRepository<UnderlyingPriceEntity, Long> {
    fun findByTickerOrderByObservedAtDesc(ticker: String): List<UnderlyingPriceEntity>
}
