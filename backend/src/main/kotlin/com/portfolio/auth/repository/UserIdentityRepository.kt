package com.portfolio.auth.repository

import com.portfolio.auth.entity.UserIdentity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface UserIdentityRepository : JpaRepository<UserIdentity, Long> {

    fun findByProviderAndProviderUserId(provider: String, providerUserId: String): UserIdentity?

    fun findByUserId(userId: Long): List<UserIdentity>

    fun findByUserIdAndProvider(userId: Long, provider: String): UserIdentity?

    @Query("SELECT ui FROM UserIdentity ui JOIN FETCH ui.user WHERE ui.provider = :provider AND ui.providerUserId = :providerUserId")
    fun findByProviderAndProviderUserIdWithUser(provider: String, providerUserId: String): UserIdentity?

    fun deleteByUserIdAndProvider(userId: Long, provider: String)
}
