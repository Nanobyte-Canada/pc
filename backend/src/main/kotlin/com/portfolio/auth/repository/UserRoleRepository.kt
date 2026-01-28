package com.portfolio.auth.repository

import com.portfolio.auth.entity.UserRole
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserRoleRepository : JpaRepository<UserRole, Long> {

    fun findByUserId(userId: Long): List<UserRole>

    fun findByUserIdAndRoleId(userId: Long, roleId: Long): UserRole?

    fun deleteByUserIdAndRoleId(userId: Long, roleId: Long)

    fun existsByUserIdAndRoleId(userId: Long, roleId: Long): Boolean
}
