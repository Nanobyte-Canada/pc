package com.portfolio.ingestion.persistence.repository

import com.portfolio.ingestion.persistence.entity.InstrumentExchange
import org.springframework.data.jpa.repository.JpaRepository

interface InstrumentExchangeRepository : JpaRepository<InstrumentExchange, Long> {
    fun findByInstrumentIdAndExchangeId(instrumentId: Long, exchangeId: Int): InstrumentExchange?
}
