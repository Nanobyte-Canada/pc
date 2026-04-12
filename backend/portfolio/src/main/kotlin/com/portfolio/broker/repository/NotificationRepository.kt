package com.portfolio.broker.repository

import com.portfolio.broker.entity.Notification
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface NotificationRepository : JpaRepository<Notification, Long> {

    fun findByUserIdOrderByCreatedAtDesc(userId: Long, pageable: Pageable): Page<Notification>

    fun findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId: Long, pageable: Pageable): Page<Notification>

    fun countByUserIdAndIsReadFalse(userId: Long): Long

    fun findByIdAndUserId(id: Long, userId: Long): Notification?

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.user.id = :userId AND n.isRead = false")
    fun markAllAsReadByUserId(userId: Long): Int
}
