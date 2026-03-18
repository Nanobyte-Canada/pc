package com.portfolio.ingestion.repository

import com.portfolio.ingestion.entity.ErrorType
import com.portfolio.ingestion.entity.IngestionError
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

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

    /**
     * Filterable errors by optional stepName and errorType strings.
     * Uses native query to avoid JPQL enum-casting complexity.
     */
    @Query(value = """
        SELECT e.* FROM ingestion_errors e
        JOIN ingestion_steps s ON e.step_id = s.id
        WHERE (:stepName IS NULL OR s.step_name = :stepName)
          AND (:errorType IS NULL OR e.error_type = :errorType)
        ORDER BY e.created_at DESC
    """, nativeQuery = true)
    fun findFilteredErrors(
        @Param("stepName") stepName: String?,
        @Param("errorType") errorType: String?,
        pageable: Pageable
    ): List<IngestionError>

    /**
     * Error counts grouped by error_type for the last N hours.
     * Returns rows as [errorType: String, count: Long, lastOccurredAt: OffsetDateTime].
     */
    @Query(value = """
        SELECT e.error_type, COUNT(*) as cnt, MAX(e.created_at) as last_at
        FROM ingestion_errors e
        WHERE e.created_at >= :since
        GROUP BY e.error_type
        ORDER BY cnt DESC
    """, nativeQuery = true)
    fun getErrorSummaryRaw(@Param("since") since: OffsetDateTime): List<Array<Any>>

    @Query("SELECT COUNT(e) FROM IngestionError e WHERE e.createdAt >= :since")
    fun countErrorsSince(@Param("since") since: OffsetDateTime): Long
}
