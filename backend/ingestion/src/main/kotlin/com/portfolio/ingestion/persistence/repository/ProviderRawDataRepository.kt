package com.portfolio.ingestion.persistence.repository

import com.portfolio.ingestion.persistence.entity.ProviderRawData
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface ProviderRawDataRepository : JpaRepository<ProviderRawData, Long> {
    fun findByInstrumentIdAndProviderAndDataType(instrumentId: Long, provider: String, dataType: String): ProviderRawData?

    @Query("SELECT i.instrumentType, COUNT(prd) FROM ProviderRawData prd JOIN prd.instrument i WHERE prd.provider = 'EODHD' AND prd.dataType = 'FUNDAMENTALS' GROUP BY i.instrumentType")
    fun countEnrichedByType(): List<Array<Any>>
}
