package com.portfolio.repository

import com.portfolio.entity.Etf
import com.portfolio.entity.EtfComEnrichmentStatus
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

    @Query("""
        SELECT e FROM Etf e
        WHERE e.sourceLastSeenAt < :cutoff
        AND e.isActive = true
    """)
    fun findStaleEtfs(@Param("cutoff") cutoff: java.time.OffsetDateTime): List<Etf>

    // ========================================
    // etf.com enrichment query methods
    // ========================================

    @Query("""
        SELECT e FROM Etf e
        WHERE e.isActive = true
        AND (
            e.etfcomEnrichmentStatus IN :statuses
            OR (e.etfcomEnrichmentStatus = 'SUCCESS' AND e.etfcomLastSuccessAt < :staleThreshold)
        )
        AND (e.etfcomEnrichmentStatus != 'FAILED_RETRYABLE' OR e.etfcomRetryCount < :maxRetries)
        AND (e.etfcomLastAttemptAt IS NULL OR e.etfcomLastAttemptAt < :retryAfter)
        ORDER BY
            CASE e.etfcomEnrichmentStatus
                WHEN 'PENDING' THEN 0
                WHEN 'FAILED_RETRYABLE' THEN 1
                WHEN 'STALE' THEN 2
                ELSE 3
            END,
            e.etfcomRetryCount ASC
    """)
    fun findEtfComEnrichmentCandidates(
        @Param("statuses") statuses: List<EtfComEnrichmentStatus>,
        @Param("maxRetries") maxRetries: Int,
        @Param("retryAfter") retryAfter: OffsetDateTime,
        @Param("staleThreshold") staleThreshold: OffsetDateTime,
        pageable: Pageable
    ): List<Etf>

    @Query("""
        SELECT COUNT(e) FROM Etf e
        WHERE e.isActive = true
        AND e.etfcomEnrichmentStatus IN ('PENDING', 'FAILED_RETRYABLE', 'STALE')
    """)
    fun countEtfComEnrichmentPending(): Long
}
