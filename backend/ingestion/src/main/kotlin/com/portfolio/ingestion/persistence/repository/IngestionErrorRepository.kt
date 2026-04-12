package com.portfolio.ingestion.persistence.repository

import com.portfolio.ingestion.persistence.entity.IngestionError
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface IngestionErrorRepository : JpaRepository<IngestionError, Long> {
    fun findByStepId(stepId: Long): List<IngestionError>
    fun findByStepRunIdOrderByCreatedAtDesc(runId: Long, pageable: Pageable): List<IngestionError>
}
