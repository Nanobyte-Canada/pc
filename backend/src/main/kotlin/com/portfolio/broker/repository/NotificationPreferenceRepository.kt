package com.portfolio.broker.repository

import com.portfolio.broker.entity.NotificationPreference
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface NotificationPreferenceRepository : JpaRepository<NotificationPreference, Long> {

    fun findByUserId(userId: Long): NotificationPreference?
}
