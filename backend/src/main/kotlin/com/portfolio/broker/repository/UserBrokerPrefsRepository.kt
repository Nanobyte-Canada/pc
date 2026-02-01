package com.portfolio.broker.repository

import com.portfolio.broker.entity.UserBrokerPrefs
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface UserBrokerPrefsRepository : JpaRepository<UserBrokerPrefs, Long> {

    fun findByUserId(userId: Long): UserBrokerPrefs?

    fun existsByUserId(userId: Long): Boolean

    @Query("""
        SELECT ubp FROM UserBrokerPrefs ubp
        WHERE ubp.autoFetchEnabled = true
        AND EXTRACT(HOUR FROM ubp.fetchTimeUtc) = :hour
    """)
    fun findUsersForFetchHour(hour: Int): List<UserBrokerPrefs>

    @Query("SELECT ubp FROM UserBrokerPrefs ubp WHERE ubp.autoFetchEnabled = true")
    fun findAllWithAutoFetchEnabled(): List<UserBrokerPrefs>

    @Query("SELECT COUNT(ubp) FROM UserBrokerPrefs ubp WHERE ubp.autoFetchEnabled = true")
    fun countUsersWithAutoFetchEnabled(): Long
}
