package com.portfolio.repository

import com.portfolio.entity.EtfHolding
import com.portfolio.entity.HoldingSourceSection
import com.portfolio.entity.ResolutionStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface EtfHoldingRepository : JpaRepository<EtfHolding, Long> {

    fun findByEtfIdAndAsOfDate(etfId: Long, asOfDate: LocalDate): List<EtfHolding>

    fun countByEtfIdAndAsOfDate(etfId: Long, asOfDate: LocalDate): Long

    fun findByEtfIdAndStockIdAndAsOfDate(etfId: Long, stockId: Long, asOfDate: LocalDate): EtfHolding?

    fun findByEtfIdAndRawTickerAndAsOfDate(etfId: Long, rawTicker: String, asOfDate: LocalDate): EtfHolding?

    fun findByEtfId(etfId: Long): List<EtfHolding>

    fun findByResolutionStatus(status: ResolutionStatus): List<EtfHolding>

    @Query("""
        SELECT eh FROM EtfHolding eh
        WHERE eh.resolutionStatus != 'RESOLVED'
    """)
    fun findUnresolvedHoldings(): List<EtfHolding>

    @Query("""
        SELECT eh FROM EtfHolding eh
        JOIN FETCH eh.stock s
        WHERE eh.etf.id = :etfId AND eh.asOfDate = :asOfDate
        ORDER BY eh.weight DESC NULLS LAST
    """)
    fun findByEtfIdAndAsOfDateWithStock(
        @Param("etfId") etfId: Long,
        @Param("asOfDate") asOfDate: LocalDate
    ): List<EtfHolding>

    @Query("""
        SELECT DISTINCT eh.asOfDate FROM EtfHolding eh
        WHERE eh.etf.id = :etfId
        ORDER BY eh.asOfDate DESC
    """)
    fun findDistinctAsOfDatesByEtfId(@Param("etfId") etfId: Long): List<LocalDate>

    @Query("""
        SELECT eh FROM EtfHolding eh
        JOIN FETCH eh.stock s
        WHERE eh.etf.id = :etfId
        AND eh.asOfDate = (
            SELECT MAX(eh2.asOfDate) FROM EtfHolding eh2
            WHERE eh2.etf.id = :etfId AND eh2.asOfDate <= :asOfDate
        )
        ORDER BY eh.weight DESC NULLS LAST
    """)
    fun findLatestHoldings(
        @Param("etfId") etfId: Long,
        @Param("asOfDate") asOfDate: LocalDate
    ): List<EtfHolding>

    @Query("""
        SELECT eh FROM EtfHolding eh
        LEFT JOIN FETCH eh.stock s
        LEFT JOIN FETCH eh.heldEtf
        WHERE eh.etf.id = :etfId
        AND eh.asOfDate = (
            SELECT MAX(eh2.asOfDate) FROM EtfHolding eh2
            WHERE eh2.etf.id = :etfId AND eh2.asOfDate <= :asOfDate
        )
        ORDER BY eh.weight DESC NULLS LAST
    """)
    fun findLatestHoldingsIncludingUnresolved(
        @Param("etfId") etfId: Long,
        @Param("asOfDate") asOfDate: LocalDate
    ): List<EtfHolding>

    @Modifying
    @Query("""
        DELETE FROM EtfHolding eh
        WHERE eh.etf.id = :etfId
        AND eh.asOfDate = :asOfDate
        AND eh.sourceSection = :sourceSection
    """)
    fun deleteByEtfIdAndAsOfDateAndSourceSection(
        @Param("etfId") etfId: Long,
        @Param("asOfDate") asOfDate: LocalDate,
        @Param("sourceSection") sourceSection: HoldingSourceSection
    ): Int

    @Modifying
    fun deleteByEtfId(etfId: Long): Int
}
