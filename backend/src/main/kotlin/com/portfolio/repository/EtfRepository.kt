package com.portfolio.repository

import com.portfolio.entity.AVEnrichmentStatus
import com.portfolio.entity.AVIngestionStatus
import com.portfolio.entity.Etf
import com.portfolio.entity.SecurityStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
interface EtfRepository : JpaRepository<Etf, Long>, JpaSpecificationExecutor<Etf> {

    fun findBySymbolIgnoreCase(symbol: String): Etf?

    fun findBySymbolIgnoreCaseAndIsActiveTrue(symbol: String): Etf?

    fun findBySymbolAndExchange(symbol: String, exchange: String): Etf?

    fun findByIsinAndIsActiveTrue(isin: String): Etf?

    fun findByCusipAndIsActiveTrue(cusip: String): Etf?

    @Query("""
        SELECT e FROM Etf e
        WHERE UPPER(e.symbol) LIKE UPPER(CONCAT(:prefix, '%'))
        ORDER BY e.symbol
    """)
    fun findBySymbolStartingWithIgnoreCase(
        @Param("prefix") prefix: String,
        pageable: Pageable
    ): List<Etf>

    @Query("""
        SELECT e FROM Etf e
        WHERE UPPER(e.symbol) LIKE UPPER(CONCAT(:prefix, '%'))
        AND e.isActive = true
        ORDER BY e.symbol
    """)
    fun findBySymbolStartingWithIgnoreCaseAndActive(
        @Param("prefix") prefix: String,
        pageable: Pageable
    ): List<Etf>

    @Query("""
        SELECT e FROM Etf e
        WHERE UPPER(e.name) LIKE UPPER(CONCAT('%', :term, '%'))
        ORDER BY e.name
    """)
    fun findByNameContainingIgnoreCase(
        @Param("term") term: String,
        pageable: Pageable
    ): List<Etf>

    @Query("""
        SELECT e FROM Etf e
        WHERE UPPER(e.name) LIKE UPPER(CONCAT('%', :term, '%'))
        AND e.isActive = true
        ORDER BY e.name
    """)
    fun findByNameContainingIgnoreCaseAndActive(
        @Param("term") term: String,
        pageable: Pageable
    ): List<Etf>

    fun findByStatus(status: SecurityStatus, pageable: Pageable): Page<Etf>

    fun findByIssuer(issuer: String, pageable: Pageable): Page<Etf>

    fun findByAssetClass(assetClass: String, pageable: Pageable): Page<Etf>

    // Ingestion-related methods
    fun findByIsin(isin: String): Etf?

    fun findByCusip(cusip: String): Etf?

    fun findByIsActiveTrue(): List<Etf>

    // TODO: Uncomment when InstrumentIdentifier entity is implemented
    // @Query("""
    //     SELECT e FROM Etf e
    //     WHERE e.isActive = true
    //     AND NOT EXISTS (
    //         SELECT 1 FROM com.portfolio.ingestion.entity.InstrumentIdentifier ii
    //         WHERE ii.instrumentType = 'ETF'
    //         AND ii.instrumentId = e.id
    //         AND ii.identifierType IN ('FIGI', 'COMPOSITE_FIGI', 'SHARE_CLASS_FIGI')
    //     )
    // """)
    // fun findMissingFigi(): List<Etf>

    @Query("""
        SELECT e FROM Etf e
        WHERE e.sourceLastSeenAt < :cutoff
        AND e.isActive = true
    """)
    fun findStaleEtfs(@Param("cutoff") cutoff: java.time.OffsetDateTime): List<Etf>

    // Alpha Vantage enrichment query methods
    @Query("""
        SELECT e FROM Etf e
        WHERE e.isActive = true
        AND (
            e.avEnrichmentStatus IN :statuses
            OR (e.avEnrichmentStatus = 'SUCCESS' AND e.avLastSuccessAt < :staleThreshold)
        )
        AND (e.avEnrichmentStatus != 'FAILED_RETRYABLE' OR e.avRetryCount < :maxRetries)
        AND (e.avLastAttemptAt IS NULL OR e.avLastAttemptAt < :retryAfter)
        ORDER BY
            CASE e.avEnrichmentStatus
                WHEN 'PENDING' THEN 0
                WHEN 'FAILED_RETRYABLE' THEN 1
                WHEN 'STALE' THEN 2
                ELSE 3
            END,
            e.avRetryCount ASC
    """)
    fun findAvEnrichmentCandidates(
        @Param("statuses") statuses: List<AVEnrichmentStatus>,
        @Param("maxRetries") maxRetries: Int,
        @Param("retryAfter") retryAfter: OffsetDateTime,
        @Param("staleThreshold") staleThreshold: OffsetDateTime,
        pageable: Pageable
    ): List<Etf>

    /**
     * Count ETFs pending Alpha Vantage enrichment
     */
    @Query("""
        SELECT COUNT(e) FROM Etf e
        WHERE e.isActive = true
        AND e.avEnrichmentStatus IN ('PENDING', 'FAILED_RETRYABLE', 'STALE')
    """)
    fun countAvEnrichmentPending(): Long

    // ========================================
    // Alpha Vantage ingestion query methods
    // ========================================

    /**
     * Find ETFs that are candidates for Alpha Vantage ingestion (fetching raw data).
     * Returns ETFs where ingestion status is PENDING, FAILED_RETRYABLE, or STALE.
     */
    @Query("""
        SELECT e FROM Etf e
        WHERE e.isActive = true
        AND (
            e.avIngestionStatus IN :statuses
            OR (e.avIngestionStatus = 'SUCCESS' AND e.avIngestionLastSuccessAt < :staleThreshold)
        )
        AND (e.avIngestionStatus != 'FAILED_RETRYABLE' OR e.avIngestionRetryCount < :maxRetries)
        AND (e.avIngestionLastAttemptAt IS NULL OR e.avIngestionLastAttemptAt < :retryAfter)
        ORDER BY
            CASE e.avIngestionStatus
                WHEN 'PENDING' THEN 0
                WHEN 'FAILED_RETRYABLE' THEN 1
                WHEN 'STALE' THEN 2
                ELSE 3
            END,
            e.avIngestionRetryCount ASC
    """)
    fun findAvIngestionCandidates(
        @Param("statuses") statuses: List<AVIngestionStatus>,
        @Param("maxRetries") maxRetries: Int,
        @Param("retryAfter") retryAfter: OffsetDateTime,
        @Param("staleThreshold") staleThreshold: OffsetDateTime,
        pageable: Pageable
    ): List<Etf>

    /**
     * Find ETFs that have successfully ingested raw data and are ready for enrichment.
     * Returns ETFs where ingestion is SUCCESS and enrichment status is PENDING, FAILED_RETRYABLE, or STALE.
     */
    @Query("""
        SELECT e FROM Etf e
        WHERE e.isActive = true
        AND e.avIngestionStatus = :ingestionStatus
        AND (
            e.avEnrichmentStatus IN :enrichmentStatuses
            OR (e.avEnrichmentStatus = 'SUCCESS' AND e.avLastSuccessAt < :staleThreshold)
        )
        AND (e.avEnrichmentStatus != 'FAILED_RETRYABLE' OR e.avRetryCount < :maxRetries)
        AND (e.avLastAttemptAt IS NULL OR e.avLastAttemptAt < :retryAfter)
        ORDER BY
            CASE e.avEnrichmentStatus
                WHEN 'PENDING' THEN 0
                WHEN 'FAILED_RETRYABLE' THEN 1
                WHEN 'STALE' THEN 2
                ELSE 3
            END,
            e.avRetryCount ASC
    """)
    fun findAvEnrichmentCandidatesWithIngestionSuccess(
        @Param("ingestionStatus") ingestionStatus: AVIngestionStatus,
        @Param("enrichmentStatuses") enrichmentStatuses: List<AVEnrichmentStatus>,
        @Param("maxRetries") maxRetries: Int,
        @Param("retryAfter") retryAfter: OffsetDateTime,
        @Param("staleThreshold") staleThreshold: OffsetDateTime,
        pageable: Pageable
    ): List<Etf>

    /**
     * Count ETFs pending Alpha Vantage ingestion
     */
    @Query("""
        SELECT COUNT(e) FROM Etf e
        WHERE e.isActive = true
        AND e.avIngestionStatus IN ('PENDING', 'FAILED_RETRYABLE', 'STALE')
    """)
    fun countAvIngestionPending(): Long
}
