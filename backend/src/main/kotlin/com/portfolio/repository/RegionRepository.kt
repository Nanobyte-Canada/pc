package com.portfolio.repository

import com.portfolio.entity.Region
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface RegionRepository : JpaRepository<Region, Long> {

    fun findByCode(code: String): Region?

    @Query("""
        SELECT r FROM Region r
        LEFT JOIN FETCH r.countries
        ORDER BY r.name
    """)
    fun findAllWithCountries(): List<Region>
}
