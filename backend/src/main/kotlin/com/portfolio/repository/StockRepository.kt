package com.portfolio.repository

import com.portfolio.entity.AVEnrichmentStatus
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
import java.time.OffsetDateTime

@Repository
interface StockRepository : JpaRepository<Stock, Long>, JpaSpecificationExecutor<Stock> {

    fun findByTickerIgnoreCase(ticker: String): Stock?

    fun findFirstByTickerIgnoreCase(ticker: String): Stock?

    fun findAllByTickerIgnoreCase(ticker: String): List<Stock>

    fun findByTickerIgnoreCaseAndIsActiveTrue(ticker: String): Stock?

    fun findByTickerAndExchange(ticker: String, exchange: String): Stock?

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

    fun findByExchange(exchange: String, pageable: Pageable): Page<Stock>

    @Query("""
        SELECT s FROM Stock s
        LEFT JOIN FETCH s.gicsSubIndustry si
        LEFT JOIN FETCH si.industry i
        LEFT JOIN FETCH i.industryGroup ig
        LEFT JOIN FETCH ig.sector
        WHERE s.id = :id
    """)
    fun findByIdWithGics(@Param("id") id: Long): Stock?

    @Query("""
        SELECT s FROM Stock s
        LEFT JOIN FETCH s.gicsSubIndustry si
        LEFT JOIN FETCH si.industry i
        LEFT JOIN FETCH i.industryGroup ig
        LEFT JOIN FETCH ig.sector
        WHERE s.id IN :ids
    """)
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

    // Alpha Vantage enrichment query methods
    @Query("""
        SELECT s FROM Stock s
        WHERE s.isActive = true
        AND (
            s.avEnrichmentStatus IN :statuses
            OR (s.avEnrichmentStatus = 'SUCCESS' AND s.avLastSuccessAt < :staleThreshold)
        )
        AND (s.avEnrichmentStatus != 'FAILED_RETRYABLE' OR s.avRetryCount < :maxRetries)
        AND (s.avLastAttemptAt IS NULL OR s.avLastAttemptAt < :retryAfter)
        ORDER BY
            CASE s.avEnrichmentStatus
                WHEN 'PENDING' THEN 0
                WHEN 'FAILED_RETRYABLE' THEN 1
                WHEN 'STALE' THEN 2
                ELSE 3
            END,
            s.avRetryCount ASC
    """)
    fun findAvEnrichmentCandidates(
        @Param("statuses") statuses: List<AVEnrichmentStatus>,
        @Param("maxRetries") maxRetries: Int,
        @Param("retryAfter") retryAfter: OffsetDateTime,
        @Param("staleThreshold") staleThreshold: OffsetDateTime,
        pageable: Pageable
    ): List<Stock>

    /**
     * Count stocks pending Alpha Vantage enrichment
     */
    @Query("""
        SELECT COUNT(s) FROM Stock s
        WHERE s.isActive = true
        AND s.avEnrichmentStatus IN ('PENDING', 'FAILED_RETRYABLE', 'STALE')
    """)
    fun countAvEnrichmentPending(): Long

    // ========================================
    // Alpha Vantage ingestion query methods
    // ========================================

    /**
     * Find stocks that are candidates for Alpha Vantage ingestion (fetching raw data).
     * Returns stocks where ingestion status is PENDING, FAILED_RETRYABLE, or STALE.
     */
    @Query("""
        SELECT s FROM Stock s
        WHERE s.isActive = true
        AND (
            s.avIngestionStatus IN :statuses
            OR (s.avIngestionStatus = 'SUCCESS' AND s.avIngestionLastSuccessAt < :staleThreshold)
        )
        AND (s.avIngestionStatus != 'FAILED_RETRYABLE' OR s.avIngestionRetryCount < :maxRetries)
        AND (s.avIngestionLastAttemptAt IS NULL OR s.avIngestionLastAttemptAt < :retryAfter)
        ORDER BY
            CASE s.avIngestionStatus
                WHEN 'PENDING' THEN 0
                WHEN 'FAILED_RETRYABLE' THEN 1
                WHEN 'STALE' THEN 2
                ELSE 3
            END,
            s.avIngestionRetryCount ASC
    """)
    fun findAvIngestionCandidates(
        @Param("statuses") statuses: List<AVIngestionStatus>,
        @Param("maxRetries") maxRetries: Int,
        @Param("retryAfter") retryAfter: OffsetDateTime,
        @Param("staleThreshold") staleThreshold: OffsetDateTime,
        pageable: Pageable
    ): List<Stock>

    /**
     * Find stocks that have successfully ingested raw data and are ready for enrichment.
     * Returns stocks where ingestion is SUCCESS and enrichment status is PENDING, FAILED_RETRYABLE, or STALE.
     */
    @Query("""
        SELECT s FROM Stock s
        WHERE s.isActive = true
        AND s.avIngestionStatus = :ingestionStatus
        AND (
            s.avEnrichmentStatus IN :enrichmentStatuses
            OR (s.avEnrichmentStatus = 'SUCCESS' AND s.avLastSuccessAt < :staleThreshold)
        )
        AND (s.avEnrichmentStatus != 'FAILED_RETRYABLE' OR s.avRetryCount < :maxRetries)
        AND (s.avLastAttemptAt IS NULL OR s.avLastAttemptAt < :retryAfter)
        ORDER BY
            CASE s.avEnrichmentStatus
                WHEN 'PENDING' THEN 0
                WHEN 'FAILED_RETRYABLE' THEN 1
                WHEN 'STALE' THEN 2
                ELSE 3
            END,
            s.avRetryCount ASC
    """)
    fun findAvEnrichmentCandidatesWithIngestionSuccess(
        @Param("ingestionStatus") ingestionStatus: AVIngestionStatus,
        @Param("enrichmentStatuses") enrichmentStatuses: List<AVEnrichmentStatus>,
        @Param("maxRetries") maxRetries: Int,
        @Param("retryAfter") retryAfter: OffsetDateTime,
        @Param("staleThreshold") staleThreshold: OffsetDateTime,
        pageable: Pageable
    ): List<Stock>

    /**
     * Count stocks pending Alpha Vantage ingestion
     */
    @Query("""
        SELECT COUNT(s) FROM Stock s
        WHERE s.isActive = true
        AND s.avIngestionStatus IN ('PENDING', 'FAILED_RETRYABLE', 'STALE')
    """)
    fun countAvIngestionPending(): Long
}
