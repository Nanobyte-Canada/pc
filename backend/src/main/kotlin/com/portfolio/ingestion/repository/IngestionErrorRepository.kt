package com.portfolio.ingestion.repository

import com.portfolio.ingestion.entity.ErrorType
import com.portfolio.ingestion.entity.IngestionError
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface IngestionErrorRepository : JpaRepository<IngestionError, Long> {

    fun findByStepId(stepId: Long): List<IngestionError>

    fun findByErrorType(errorType: ErrorType): List<IngestionError>

    @Query("""
        SELECT e FROM IngestionError e
        WHERE e.step.run.id = :runId
        ORDER BY e.createdAt DESC
    """)
    fun findByRunId(runId: Long): List<IngestionError>

    @Query("SELECT e FROM IngestionError e ORDER BY e.createdAt DESC")
    fun findRecentErrors(pageable: Pageable): List<IngestionError>

    @Query("""
        SELECT e.errorType, COUNT(e)
        FROM IngestionError e
        WHERE e.step.run.id = :runId
        GROUP BY e.errorType
    """)
    fun countByErrorTypeForRun(runId: Long): List<Array<Any>>
}
