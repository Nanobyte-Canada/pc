package com.portfolio.marketdata.db.repository

import com.portfolio.marketdata.db.entity.IvObservationEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface IvObservationRepository : JpaRepository<IvObservationEntity, Long> {
    fun findByTickerAndObservedDateAfter(ticker: String, after: LocalDate): List<IvObservationEntity>
    fun findByTickerAndObservedDate(ticker: String, date: LocalDate): IvObservationEntity?
}
