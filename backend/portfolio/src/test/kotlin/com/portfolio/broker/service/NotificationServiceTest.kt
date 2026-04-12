package com.portfolio.broker.service

import com.portfolio.auth.entity.User
import com.portfolio.broker.dto.UpdateNotificationPreferenceRequest
import com.portfolio.broker.entity.Notification
import com.portfolio.broker.entity.NotificationPreference
import com.portfolio.broker.entity.NotificationType
import com.portfolio.broker.repository.NotificationPreferenceRepository
import com.portfolio.broker.repository.NotificationRepository
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NotificationServiceTest {

    private lateinit var service: NotificationService
    private lateinit var notificationRepository: NotificationRepository
    private lateinit var preferenceRepository: NotificationPreferenceRepository

    @BeforeEach
    fun setup() {
        notificationRepository = mockk()
        preferenceRepository = mockk()
        service = NotificationService(notificationRepository, preferenceRepository)
    }

    @Test
    fun `creates notification successfully`() {
        val user = createUser(1L)
        every { notificationRepository.save(any()) } answers { firstArg() }

        val result = service.createNotification(
            user = user,
            type = NotificationType.DRIFT_ALERT,
            title = "Portfolio Drift",
            message = "Your portfolio has drifted below 85% accuracy"
        )

        assertEquals("DRIFT_ALERT", result.type)
        assertEquals("Portfolio Drift", result.title)
        verify { notificationRepository.save(any()) }
    }

    @Test
    fun `returns notifications with unread count`() {
        val user = createUser(1L)
        val notification = Notification(
            id = 1L,
            user = user,
            type = NotificationType.ORDER_FILLED,
            title = "Order Filled",
            message = "Buy 10 VFV filled"
        )
        val page = PageImpl(listOf(notification))

        every { notificationRepository.findByUserIdOrderByCreatedAtDesc(1L, any()) } returns page
        every { notificationRepository.countByUserIdAndIsReadFalse(1L) } returns 3L

        val response = service.getNotifications(1L)

        assertEquals(1, response.notifications.size)
        assertEquals(3L, response.unreadCount)
    }

    @Test
    fun `marks notification as read`() {
        val user = createUser(1L)
        val notification = Notification(
            id = 5L,
            user = user,
            type = NotificationType.SYSTEM,
            title = "Test",
            message = "Test message"
        )

        every { notificationRepository.findByIdAndUserId(5L, 1L) } returns notification
        every { notificationRepository.save(any()) } answers { firstArg() }

        val result = service.markAsRead(1L, 5L)

        assertTrue(result.isRead)
    }

    @Test
    fun `mark as read fails for non-existent notification`() {
        every { notificationRepository.findByIdAndUserId(99L, 1L) } returns null

        assertFailsWith<IllegalArgumentException> {
            service.markAsRead(1L, 99L)
        }
    }

    @Test
    fun `updates preferences correctly`() {
        val user = createUser(1L)
        val pref = NotificationPreference(id = 1L, user = user)

        every { preferenceRepository.findByUserId(1L) } returns pref
        every { preferenceRepository.save(any()) } answers { firstArg() }

        val result = service.updatePreferences(user, UpdateNotificationPreferenceRequest(
            driftAlerts = false,
            driftThreshold = BigDecimal("85.00")
        ))

        assertEquals(false, result.driftAlerts)
        assertEquals(BigDecimal("85.00"), result.driftThreshold)
    }

    @Test
    fun `returns default preferences when none exist`() {
        every { preferenceRepository.findByUserId(1L) } returns null

        val result = service.getPreferences(1L)

        assertTrue(result.emailEnabled)
        assertTrue(result.driftAlerts)
        assertEquals(BigDecimal("90.00"), result.driftThreshold)
    }

    private fun createUser(id: Long): User {
        return User(id = id, email = "user$id@example.com", passwordHash = "hash", name = "Test User")
    }
}
