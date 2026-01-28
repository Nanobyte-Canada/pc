package com.portfolio.auth.repository

import com.portfolio.auth.entity.PasswordResetToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
interface PasswordResetTokenRepository : JpaRepository<PasswordResetToken, Long> {

    fun findByTokenHash(tokenHash: String): PasswordResetToken?

    @Query("SELECT prt FROM PasswordResetToken prt JOIN FETCH prt.user WHERE prt.tokenHash = :tokenHash")
    fun findByTokenHashWithUser(tokenHash: String): PasswordResetToken?

    fun findByUserId(userId: Long): List<PasswordResetToken>

    @Modifying
    @Query("DELETE FROM PasswordResetToken prt WHERE prt.expiresAt < :before OR prt.usedAt IS NOT NULL")
    fun deleteExpiredAndUsed(before: OffsetDateTime = OffsetDateTime.now())
}
