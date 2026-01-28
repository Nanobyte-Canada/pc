package com.portfolio.repository

import com.portfolio.entity.Country
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface CountryRepository : JpaRepository<Country, Long> {

    fun findByCode(code: String): Country?

    @Query("""
        SELECT c FROM Country c
        JOIN FETCH c.region
        WHERE c.code = :code
    """)
    fun findByCodeWithRegion(@Param("code") code: String): Country?

    @Query("""
        SELECT c FROM Country c
        JOIN FETCH c.region
        ORDER BY c.name
    """)
    fun findAllWithRegion(): List<Country>

    @Query("""
        SELECT c FROM Country c
        JOIN FETCH c.region r
        WHERE r.code = :regionCode
        ORDER BY c.name
    """)
    fun findByRegionCode(@Param("regionCode") regionCode: String): List<Country>
}
