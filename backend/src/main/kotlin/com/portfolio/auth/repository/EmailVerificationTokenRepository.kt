package com.portfolio.auth.repository

import com.portfolio.auth.entity.EmailVerificationToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
interface EmailVerificationTokenRepository : JpaRepository<EmailVerificationToken, Long> {

    fun findByTokenHash(tokenHash: String): EmailVerificationToken?

    @Query("SELECT evt FROM EmailVerificationToken evt JOIN FETCH evt.user WHERE evt.tokenHash = :tokenHash")
    fun findByTokenHashWithUser(tokenHash: String): EmailVerificationToken?

    fun findByUserId(userId: Long): List<EmailVerificationToken>

    @Modifying
    @Query("DELETE FROM EmailVerificationToken evt WHERE evt.expiresAt < :before OR evt.usedAt IS NOT NULL")
    fun deleteExpiredAndUsed(before: OffsetDateTime = OffsetDateTime.now())
}
