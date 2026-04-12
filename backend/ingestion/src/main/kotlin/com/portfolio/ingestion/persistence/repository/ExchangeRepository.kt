package com.portfolio.ingestion.persistence.repository

import com.portfolio.ingestion.persistence.entity.Exchange
import org.springframework.data.jpa.repository.JpaRepository

interface ExchangeRepository : JpaRepository<Exchange, Int> {
    fun findByCode(code: String): Exchange?
    fun findByIsActiveTrue(): List<Exchange>
    fun countByIsActiveTrue(): Long
}
