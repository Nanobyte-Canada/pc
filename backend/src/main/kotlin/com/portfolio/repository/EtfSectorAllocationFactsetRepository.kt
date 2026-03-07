package com.portfolio.repository

import com.portfolio.entity.EtfSectorAllocationFactset
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface EtfSectorAllocationFactsetRepository : JpaRepository<EtfSectorAllocationFactset, Long> {

    fun findByEtfIdAndAsOfDate(etfId: Long, asOfDate: LocalDate): List<EtfSectorAllocationFactset>

    @Query("""
        SELECT sa FROM EtfSectorAllocationFactset sa
        WHERE sa.etf.id = :etfId
        AND sa.asOfDate = (
            SELECT MAX(sa2.asOfDate) FROM EtfSectorAllocationFactset sa2
            WHERE sa2.etf.id = :etfId
        )
    """)
    fun findLatestByEtfId(@Param("etfId") etfId: Long): List<EtfSectorAllocationFactset>

    @Modifying
    @Query("""
        DELETE FROM EtfSectorAllocationFactset sa
        WHERE sa.etf.id = :etfId AND sa.asOfDate = :asOfDate
    """)
    fun deleteByEtfIdAndAsOfDate(
        @Param("etfId") etfId: Long,
        @Param("asOfDate") asOfDate: LocalDate
    ): Int
}
