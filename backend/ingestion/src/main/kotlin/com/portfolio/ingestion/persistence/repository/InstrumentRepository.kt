package com.portfolio.ingestion.persistence.repository

import com.portfolio.ingestion.persistence.entity.Instrument
import com.portfolio.ingestion.persistence.entity.InstrumentType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface InstrumentRepository : JpaRepository<Instrument, Long> {
    fun findByIsin(isin: String): Instrument?
    fun findByTickerAndInstrumentType(ticker: String, instrumentType: InstrumentType): Instrument?
    fun findByTickerAndInstrumentTypeAndCurrency(ticker: String, instrumentType: InstrumentType, currency: String): Instrument?

    @Query("""
        SELECT i FROM Instrument i
        LEFT JOIN ProviderRawData prd ON prd.instrument.id = i.id
            AND prd.provider = 'EODHD' AND prd.dataType = 'FUNDAMENTALS'
        WHERE i.status = 'ACTIVE'
        ORDER BY prd.fetchedAt ASC NULLS FIRST,
            CASE i.instrumentType
                WHEN 'STOCK' THEN 1
                WHEN 'ETF' THEN 2
                WHEN 'MUTUAL_FUND' THEN 3
                WHEN 'PREFERRED_STOCK' THEN 4
                WHEN 'BOND' THEN 5
                WHEN 'INDEX' THEN 6
                ELSE 7
            END
    """)
    fun findStaleInstruments(pageable: Pageable): List<Instrument>

    @Query("SELECT i.instrumentType, COUNT(i) FROM Instrument i WHERE i.status = 'ACTIVE' GROUP BY i.instrumentType")
    fun countByType(): List<Array<Any>>
}
