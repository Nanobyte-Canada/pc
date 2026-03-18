package com.portfolio.ingestion.repository

import com.portfolio.ingestion.entity.IngestionRun
import com.portfolio.ingestion.entity.RunStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface IngestionRunRepository : JpaRepository<IngestionRun, Long> {

    @Query("SELECT r FROM IngestionRun r ORDER BY r.startedAt DESC")
    fun findRecentRuns(pageable: Pageable): List<IngestionRun>

    @Query("""
        SELECT r FROM IngestionRun r
        LEFT JOIN FETCH r.steps
        WHERE r.id = :id
    """)
    fun findByIdWithSteps(id: Long): IngestionRun?

    @Query("""
        SELECT r FROM IngestionRun r
        LEFT JOIN FETCH r.steps s
        WHERE r.id = :id
    """)
    fun findByIdWithStepsAndErrors(id: Long): IngestionRun?

    fun findByStatus(status: RunStatus): List<IngestionRun>

    @Query("""
        SELECT r FROM IngestionRun r
        WHERE r.status = 'RUNNING'
    """)
    fun findRunningRuns(): List<IngestionRun>
}
