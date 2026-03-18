package com.portfolio.repository

import com.portfolio.entity.gics.GicsIndustry
import com.portfolio.entity.gics.GicsIndustryGroup
import com.portfolio.entity.gics.GicsSector
import com.portfolio.entity.gics.GicsSectorAlias
import com.portfolio.entity.gics.GicsSubIndustry
import com.portfolio.entity.gics.GicsSubIndustryAlias
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface GicsSectorRepository : JpaRepository<GicsSector, Long> {
    fun findByCode(code: String): GicsSector?

    @Query("""
        SELECT DISTINCT s FROM GicsSector s
        LEFT JOIN FETCH s.industryGroups ig
        LEFT JOIN FETCH ig.industries i
        LEFT JOIN FETCH i.subIndustries
        ORDER BY s.code
    """)
    fun findAllWithHierarchy(): List<GicsSector>
}

@Repository
interface GicsIndustryGroupRepository : JpaRepository<GicsIndustryGroup, Long> {
    fun findByCode(code: String): GicsIndustryGroup?
    fun findBySectorId(sectorId: Long): List<GicsIndustryGroup>
}

@Repository
interface GicsIndustryRepository : JpaRepository<GicsIndustry, Long> {
    fun findByCode(code: String): GicsIndustry?
    fun findByIndustryGroupId(industryGroupId: Long): List<GicsIndustry>
}

@Repository
interface GicsSubIndustryRepository : JpaRepository<GicsSubIndustry, Long> {
    fun findByCode(code: String): GicsSubIndustry?
    fun findByIndustryId(industryId: Long): List<GicsSubIndustry>
}

@Repository
interface GicsSectorAliasRepository : JpaRepository<GicsSectorAlias, Long> {
    fun findByAliasValueAndSource(aliasValue: String, source: String): GicsSectorAlias?

    @Query("SELECT a FROM GicsSectorAlias a WHERE LOWER(a.aliasValue) = LOWER(:aliasValue) AND a.source = :source")
    fun findByAliasValueIgnoreCaseAndSource(aliasValue: String, source: String): GicsSectorAlias?

    fun findBySource(source: String): List<GicsSectorAlias>
}

@Repository
interface GicsSubIndustryAliasRepository : JpaRepository<GicsSubIndustryAlias, Long> {
    fun findByAliasCodeAndSource(aliasCode: String, source: String): GicsSubIndustryAlias?
    fun findBySource(source: String): List<GicsSubIndustryAlias>

    @Query("SELECT a FROM GicsSubIndustryAlias a WHERE LOWER(a.aliasName) = LOWER(:aliasName) AND a.source = :source")
    fun findByAliasNameIgnoreCaseAndSource(aliasName: String, source: String): GicsSubIndustryAlias?
}
