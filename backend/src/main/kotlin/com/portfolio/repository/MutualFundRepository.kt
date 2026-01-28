package com.portfolio.repository

import com.portfolio.entity.MutualFund
import com.portfolio.entity.MutualFundStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface MutualFundRepository : JpaRepository<MutualFund, Long>, JpaSpecificationExecutor<MutualFund> {

    fun findBySymbolIgnoreCase(symbol: String): MutualFund?

    fun findBySymbolIgnoreCaseAndIsActiveTrue(symbol: String): MutualFund?

    fun findBySymbol(symbol: String): MutualFund?

    fun findByIsinAndIsActiveTrue(isin: String): MutualFund?

    fun findByCusipAndIsActiveTrue(cusip: String): MutualFund?

    @Query("""
        SELECT mf FROM MutualFund mf
        WHERE UPPER(mf.symbol) LIKE UPPER(CONCAT(:prefix, '%'))
        ORDER BY mf.symbol
    """)
    fun findBySymbolStartingWithIgnoreCase(
        @Param("prefix") prefix: String,
        pageable: Pageable
    ): List<MutualFund>

    @Query("""
        SELECT mf FROM MutualFund mf
        WHERE UPPER(mf.symbol) LIKE UPPER(CONCAT(:prefix, '%'))
        AND mf.isActive = true
        ORDER BY mf.symbol
    """)
    fun findBySymbolStartingWithIgnoreCaseAndActive(
        @Param("prefix") prefix: String,
        pageable: Pageable
    ): List<MutualFund>

    @Query("""
        SELECT mf FROM MutualFund mf
        WHERE UPPER(mf.name) LIKE UPPER(CONCAT('%', :term, '%'))
        ORDER BY mf.name
    """)
    fun findByNameContainingIgnoreCase(
        @Param("term") term: String,
        pageable: Pageable
    ): List<MutualFund>

    @Query("""
        SELECT mf FROM MutualFund mf
        WHERE UPPER(mf.name) LIKE UPPER(CONCAT('%', :term, '%'))
        AND mf.isActive = true
        ORDER BY mf.name
    """)
    fun findByNameContainingIgnoreCaseAndActive(
        @Param("term") term: String,
        pageable: Pageable
    ): List<MutualFund>

    fun findByStatus(status: MutualFundStatus, pageable: Pageable): Page<MutualFund>

    fun findByIssuer(issuer: String, pageable: Pageable): Page<MutualFund>

    fun findByAssetClass(assetClass: String, pageable: Pageable): Page<MutualFund>

    fun findByFundType(fundType: String, pageable: Pageable): Page<MutualFund>

    // Ingestion-related methods
    fun findByIsin(isin: String): MutualFund?

    fun findByCusip(cusip: String): MutualFund?

    fun findByIsActiveTrue(): List<MutualFund>

    // TODO: Uncomment when InstrumentIdentifier entity is implemented
    // @Query("""
    //     SELECT mf FROM MutualFund mf
    //     WHERE mf.isActive = true
    //     AND NOT EXISTS (
    //         SELECT 1 FROM com.portfolio.ingestion.entity.InstrumentIdentifier ii
    //         WHERE ii.instrumentType = 'MUTUAL_FUND'
    //         AND ii.instrumentId = mf.id
    //         AND ii.identifierType IN ('FIGI', 'COMPOSITE_FIGI', 'SHARE_CLASS_FIGI')
    //     )
    // """)
    // fun findMissingFigi(): List<MutualFund>

    @Query("""
        SELECT mf FROM MutualFund mf
        WHERE mf.sourceLastSeenAt < :cutoff
        AND mf.isActive = true
    """)
    fun findStaleMutualFunds(@Param("cutoff") cutoff: java.time.OffsetDateTime): List<MutualFund>
}
