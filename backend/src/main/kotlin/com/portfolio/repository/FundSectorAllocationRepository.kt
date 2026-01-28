package com.portfolio.repository

import com.portfolio.entity.AllocationParentType
import com.portfolio.entity.AllocationSource
import com.portfolio.entity.FundSectorAllocation
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface FundSectorAllocationRepository : JpaRepository<FundSectorAllocation, Long> {

    fun findByParentTypeAndParentId(
        parentType: AllocationParentType,
        parentId: Long
    ): List<FundSectorAllocation>

    fun findByParentTypeAndParentIdAndAsOfDate(
        parentType: AllocationParentType,
        parentId: Long,
        asOfDate: LocalDate
    ): List<FundSectorAllocation>

    fun findByParentTypeAndParentIdAndAsOfDateAndSource(
        parentType: AllocationParentType,
        parentId: Long,
        asOfDate: LocalDate,
        source: AllocationSource
    ): FundSectorAllocation?

    @Query("""
        SELECT fsa FROM FundSectorAllocation fsa
        WHERE fsa.parentType = :parentType
        AND fsa.parentId = :parentId
        ORDER BY fsa.asOfDate DESC
    """)
    fun findLatestByParentTypeAndParentId(
        @Param("parentType") parentType: AllocationParentType,
        @Param("parentId") parentId: Long
    ): List<FundSectorAllocation>

    @Modifying
    @Query("""
        DELETE FROM FundSectorAllocation fsa
        WHERE fsa.parentType = :parentType
        AND fsa.parentId = :parentId
        AND fsa.asOfDate = :asOfDate
        AND fsa.source = :source
    """)
    fun deleteByParentTypeAndParentIdAndAsOfDateAndSource(
        @Param("parentType") parentType: AllocationParentType,
        @Param("parentId") parentId: Long,
        @Param("asOfDate") asOfDate: LocalDate,
        @Param("source") source: AllocationSource
    ): Int
}
