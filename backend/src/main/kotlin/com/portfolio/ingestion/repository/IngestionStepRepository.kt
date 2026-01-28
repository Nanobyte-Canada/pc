package com.portfolio.ingestion.repository

import com.portfolio.ingestion.entity.IngestionStep
import com.portfolio.ingestion.entity.StepName
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface IngestionStepRepository : JpaRepository<IngestionStep, Long> {

    fun findByRunId(runId: Long): List<IngestionStep>

    fun findByRunIdAndStepName(runId: Long, stepName: StepName): IngestionStep?

    @Query("""
        SELECT s FROM IngestionStep s
        LEFT JOIN FETCH s.errors
        WHERE s.id = :id
    """)
    fun findByIdWithErrors(id: Long): IngestionStep?
}
