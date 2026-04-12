package com.portfolio.auth.repository

import com.portfolio.auth.entity.AuditEventType
import com.portfolio.auth.entity.AuditLog
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
interface AuditLogRepository : JpaRepository<AuditLog, Long> {

    fun findByUserId(userId: Long, pageable: Pageable): Page<AuditLog>

    fun findByEventType(eventType: AuditEventType, pageable: Pageable): Page<AuditLog>

    @Query("SELECT al FROM AuditLog al WHERE al.createdAt BETWEEN :from AND :to ORDER BY al.createdAt DESC")
    fun findByDateRange(from: OffsetDateTime, to: OffsetDateTime, pageable: Pageable): Page<AuditLog>

    @Query("SELECT al FROM AuditLog al WHERE al.user.id = :userId AND al.eventType = :eventType ORDER BY al.createdAt DESC")
    fun findByUserIdAndEventType(userId: Long, eventType: AuditEventType, pageable: Pageable): Page<AuditLog>
}
