package com.portfolio.repository

import com.portfolio.entity.EtfHolding
import com.portfolio.entity.HoldingSourceSection
import com.portfolio.entity.MutualFundHolding
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
        LEFT JOIN FETCH s.gicsSubIndustry si
        LEFT JOIN FETCH si.industry i
        LEFT JOIN FETCH i.industryGroup ig
        LEFT JOIN FETCH ig.sector
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
        LEFT JOIN FETCH s.gicsSubIndustry si
        LEFT JOIN FETCH si.industry i
        LEFT JOIN FETCH i.industryGroup ig
        LEFT JOIN FETCH ig.sector
        LEFT JOIN FETCH eh.heldEtf
        LEFT JOIN FETCH eh.heldMutualFund
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
}

@Repository
interface MutualFundHoldingRepository : JpaRepository<MutualFundHolding, Long> {

    fun findByMutualFundIdAndAsOfDate(mutualFundId: Long, asOfDate: LocalDate): List<MutualFundHolding>

    fun countByMutualFundIdAndAsOfDate(mutualFundId: Long, asOfDate: LocalDate): Long

    fun findByMutualFundId(mutualFundId: Long): List<MutualFundHolding>

    fun findByResolutionStatus(status: ResolutionStatus): List<MutualFundHolding>

    @Query("""
        SELECT mfh FROM MutualFundHolding mfh
        WHERE mfh.resolutionStatus != 'RESOLVED'
    """)
    fun findUnresolvedMutualFundHoldings(): List<MutualFundHolding>

    @Query("""
        SELECT mfh FROM MutualFundHolding mfh
        JOIN FETCH mfh.stock s
        LEFT JOIN FETCH s.gicsSubIndustry si
        LEFT JOIN FETCH si.industry i
        LEFT JOIN FETCH i.industryGroup ig
        LEFT JOIN FETCH ig.sector
        WHERE mfh.mutualFund.id = :mutualFundId AND mfh.asOfDate = :asOfDate
        ORDER BY mfh.weight DESC NULLS LAST
    """)
    fun findByMutualFundIdAndAsOfDateWithStock(
        @Param("mutualFundId") mutualFundId: Long,
        @Param("asOfDate") asOfDate: LocalDate
    ): List<MutualFundHolding>

    @Query("""
        SELECT DISTINCT mfh.asOfDate FROM MutualFundHolding mfh
        WHERE mfh.mutualFund.id = :mutualFundId
        ORDER BY mfh.asOfDate DESC
    """)
    fun findDistinctAsOfDatesByMutualFundId(@Param("mutualFundId") mutualFundId: Long): List<LocalDate>

    @Query("""
        SELECT mfh FROM MutualFundHolding mfh
        JOIN FETCH mfh.stock s
        WHERE mfh.mutualFund.id = :mutualFundId
        AND mfh.asOfDate = (
            SELECT MAX(mfh2.asOfDate) FROM MutualFundHolding mfh2
            WHERE mfh2.mutualFund.id = :mutualFundId AND mfh2.asOfDate <= :asOfDate
        )
        ORDER BY mfh.weight DESC NULLS LAST
    """)
    fun findLatestHoldings(
        @Param("mutualFundId") mutualFundId: Long,
        @Param("asOfDate") asOfDate: LocalDate
    ): List<MutualFundHolding>

    @Query("""
        SELECT mfh FROM MutualFundHolding mfh
        LEFT JOIN FETCH mfh.stock s
        LEFT JOIN FETCH s.gicsSubIndustry si
        LEFT JOIN FETCH si.industry i
        LEFT JOIN FETCH i.industryGroup ig
        LEFT JOIN FETCH ig.sector
        LEFT JOIN FETCH mfh.heldEtf
        LEFT JOIN FETCH mfh.heldMutualFund
        WHERE mfh.mutualFund.id = :mutualFundId
        AND mfh.asOfDate = (
            SELECT MAX(mfh2.asOfDate) FROM MutualFundHolding mfh2
            WHERE mfh2.mutualFund.id = :mutualFundId AND mfh2.asOfDate <= :asOfDate
        )
        ORDER BY mfh.weight DESC NULLS LAST
    """)
    fun findLatestHoldingsIncludingUnresolved(
        @Param("mutualFundId") mutualFundId: Long,
        @Param("asOfDate") asOfDate: LocalDate
    ): List<MutualFundHolding>

    @Modifying
    @Query("""
        DELETE FROM MutualFundHolding mfh
        WHERE mfh.mutualFund.id = :mutualFundId
        AND mfh.asOfDate = :asOfDate
        AND mfh.sourceSection = :sourceSection
    """)
    fun deleteByMutualFundIdAndAsOfDateAndSourceSection(
        @Param("mutualFundId") mutualFundId: Long,
        @Param("asOfDate") asOfDate: LocalDate,
        @Param("sourceSection") sourceSection: HoldingSourceSection
    ): Int
}
