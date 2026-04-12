package com.portfolio.auth.repository

import com.portfolio.auth.entity.RefreshToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {

    fun findByTokenHash(tokenHash: String): RefreshToken?

    @Query("SELECT rt FROM RefreshToken rt JOIN FETCH rt.user WHERE rt.tokenHash = :tokenHash")
    fun findByTokenHashWithUser(tokenHash: String): RefreshToken?

    fun findByUserId(userId: Long): List<RefreshToken>

    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revokedAt = :revokedAt, rt.revokedReason = :reason WHERE rt.user.id = :userId AND rt.revokedAt IS NULL")
    fun revokeAllByUserId(userId: Long, revokedAt: OffsetDateTime = OffsetDateTime.now(), reason: String = "logout_all")

    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :before OR rt.revokedAt IS NOT NULL")
    fun deleteExpiredAndRevoked(before: OffsetDateTime = OffsetDateTime.now())
}
