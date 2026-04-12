package com.portfolio.auth.repository

import com.portfolio.auth.entity.User
import com.portfolio.auth.entity.UserStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface UserRepository : JpaRepository<User, Long> {

    fun findByEmail(email: String): User?

    fun existsByEmail(email: String): Boolean

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.userRoles ur LEFT JOIN FETCH ur.role WHERE u.email = :email")
    fun findByEmailWithRoles(email: String): User?

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.userRoles ur LEFT JOIN FETCH ur.role WHERE u.id = :id")
    fun findByIdWithRoles(id: Long): User?

    @Query("SELECT r.name FROM UserRole ur JOIN ur.role r WHERE ur.user.id = :userId")
    fun findRoleNamesByUserId(userId: Long): List<String>

    fun findByStatus(status: UserStatus): List<User>

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.identities WHERE u.id = :id")
    fun findByIdWithIdentities(id: Long): User?
}
