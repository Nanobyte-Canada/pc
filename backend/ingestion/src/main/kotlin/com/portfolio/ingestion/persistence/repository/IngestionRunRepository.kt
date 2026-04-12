package com.portfolio.ingestion.persistence.repository

import com.portfolio.ingestion.persistence.entity.IngestionRun
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface IngestionRunRepository : JpaRepository<IngestionRun, Long> {
    fun findAllByOrderByStartedAtDesc(pageable: Pageable): List<IngestionRun>
}
