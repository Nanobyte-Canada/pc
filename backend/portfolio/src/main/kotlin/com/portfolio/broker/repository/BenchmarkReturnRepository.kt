package com.portfolio.broker.repository

import com.portfolio.broker.entity.BenchmarkReturn
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface BenchmarkReturnRepository : JpaRepository<BenchmarkReturn, Long> {

    fun findBySymbolAndReturnDateBetweenOrderByReturnDateAsc(
        symbol: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<BenchmarkReturn>
}
