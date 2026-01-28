package com.portfolio.repository

import com.portfolio.entity.DataSource
import com.portfolio.entity.IngestionBatch
import com.portfolio.entity.IngestionStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface DataSourceRepository : JpaRepository<DataSource, Long> {
    fun findByCode(code: String): DataSource?
    fun findByIsActiveTrue(): List<DataSource>
}

@Repository
interface IngestionBatchRepository : JpaRepository<IngestionBatch, Long> {
    fun findBySourceId(sourceId: Long): List<IngestionBatch>
    fun findByBatchDate(batchDate: LocalDate): List<IngestionBatch>
    fun findByStatus(status: IngestionStatus): List<IngestionBatch>
}
