package com.portfolio.repository

import com.portfolio.entity.AVIngestionStatus
import com.portfolio.entity.SecurityStatus
import com.portfolio.entity.Stock
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface StockRepository : JpaRepository<Stock, Long>, JpaSpecificationExecutor<Stock> {

    fun findByTickerIgnoreCase(ticker: String): Stock?

    fun findFirstByTickerIgnoreCase(ticker: String): Stock?

    fun findAllByTickerIgnoreCase(ticker: String): List<Stock>

    fun findByTickerIgnoreCaseAndIsActiveTrue(ticker: String): Stock?

    fun findByIsinAndIsActiveTrue(isin: String): Stock?

    fun findByCusipAndIsActiveTrue(cusip: String): Stock?

    fun findBySedol(sedol: String): Stock?

    fun findBySedolAndIsActiveTrue(sedol: String): Stock?

    @Query("""
        SELECT s FROM Stock s
        WHERE UPPER(s.ticker) LIKE UPPER(CONCAT(:prefix, '%'))
        ORDER BY s.ticker
    """)
    fun findByTickerStartingWithIgnoreCase(
        @Param("prefix") prefix: String,
        pageable: Pageable
    ): List<Stock>

    @Query("""
        SELECT s FROM Stock s
        WHERE UPPER(s.ticker) LIKE UPPER(CONCAT(:prefix, '%'))
        AND s.isActive = true
        ORDER BY s.ticker
    """)
    fun findByTickerStartingWithIgnoreCaseAndActive(
        @Param("prefix") prefix: String,
        pageable: Pageable
    ): List<Stock>

    @Query("""
        SELECT s FROM Stock s
        WHERE UPPER(s.name) LIKE UPPER(CONCAT('%', :term, '%'))
        ORDER BY s.name
    """)
    fun findByNameContainingIgnoreCase(
        @Param("term") term: String,
        pageable: Pageable
    ): List<Stock>

    @Query("""
        SELECT s FROM Stock s
        WHERE UPPER(s.name) LIKE UPPER(CONCAT('%', :term, '%'))
        AND s.isActive = true
        ORDER BY s.name
    """)
    fun findByNameContainingIgnoreCaseAndActive(
        @Param("term") term: String,
        pageable: Pageable
    ): List<Stock>

    fun findByStatus(status: SecurityStatus, pageable: Pageable): Page<Stock>

    fun findByCountry(country: String, pageable: Pageable): Page<Stock>

    @Query("SELECT s FROM Stock s WHERE s.id = :id")
    fun findByIdWithGics(@Param("id") id: Long): Stock?

    @Query("SELECT s FROM Stock s WHERE s.id IN :ids")
    fun findAllByIdWithGics(@Param("ids") ids: Collection<Long>): List<Stock>

    // Ingestion-related methods
    fun findByIsin(isin: String): Stock?

    fun findByCusip(cusip: String): Stock?

    fun findByIsActiveTrue(): List<Stock>

    // TODO: Uncomment when InstrumentIdentifier entity is implemented
    // @Query("""
    //     SELECT s FROM Stock s
    //     WHERE s.isActive = true
    //     AND NOT EXISTS (
    //         SELECT 1 FROM com.portfolio.ingestion.entity.InstrumentIdentifier ii
    //         WHERE ii.instrumentType = 'STOCK'
    //         AND ii.instrumentId = s.id
    //         AND ii.identifierType IN ('FIGI', 'COMPOSITE_FIGI', 'SHARE_CLASS_FIGI')
    //     )
    // """)
    // fun findMissingFigi(): List<Stock>

    @Query("""
        SELECT s FROM Stock s
        WHERE s.sourceLastSeenAt < :cutoff
        AND s.isActive = true
    """)
    fun findStaleStocks(@Param("cutoff") cutoff: java.time.OffsetDateTime): List<Stock>

    // Alpha Vantage ingestion query methods
    // ========================================

    @Query("""
        SELECT COUNT(s) FROM Stock s
        WHERE s.isActive = true
        AND s.avIngestionStatus IN ('PENDING', 'FAILED_RETRYABLE', 'STALE')
    """)
    fun countAvIngestionPending(): Long

    fun countByAvIngestionStatus(status: AVIngestionStatus): Long
}
