package com.portfolio.ingestion.persistence.repository

import com.portfolio.ingestion.persistence.entity.IngestionStep
import org.springframework.data.jpa.repository.JpaRepository

interface IngestionStepRepository : JpaRepository<IngestionStep, Long> {
    fun findByRunId(runId: Long): List<IngestionStep>
}
