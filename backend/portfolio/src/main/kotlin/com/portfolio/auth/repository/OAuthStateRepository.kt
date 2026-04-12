package com.portfolio.auth.repository

import com.portfolio.auth.entity.OAuthState
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
interface OAuthStateRepository : JpaRepository<OAuthState, Long> {

    fun findByStateHash(stateHash: String): OAuthState?

    @Modifying
    @Query("DELETE FROM OAuthState os WHERE os.expiresAt < :before OR os.usedAt IS NOT NULL")
    fun deleteExpiredAndUsed(before: OffsetDateTime = OffsetDateTime.now())
}
