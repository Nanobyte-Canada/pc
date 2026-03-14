package com.portfolio.broker.controller

import com.portfolio.auth.repository.UserRepository
import com.portfolio.auth.security.UserPrincipal
import com.portfolio.broker.dto.*
import com.portfolio.broker.service.NotificationService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/notifications")
@PreAuthorize("isAuthenticated()")
class NotificationController(
    private val notificationService: NotificationService,
    private val userRepository: UserRepository
) {
    @GetMapping
    fun getNotifications(
        @RequestParam(defaultValue = "false") unreadOnly: Boolean,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<NotificationsResponse> {
        val response = notificationService.getNotifications(principal.id, unreadOnly, page, size)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/count")
    fun getUnreadCount(
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<Map<String, Long>> {
        val count = notificationService.getUnreadCount(principal.id)
        return ResponseEntity.ok(mapOf("unreadCount" to count))
    }

    @PostMapping("/{id}/read")
    fun markAsRead(
        @PathVariable id: Long,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<NotificationDto> {
        val notification = notificationService.markAsRead(principal.id, id)
        return ResponseEntity.ok(notification)
    }

    @PostMapping("/read-all")
    fun markAllAsRead(
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<Map<String, Int>> {
        val count = notificationService.markAllAsRead(principal.id)
        return ResponseEntity.ok(mapOf("markedCount" to count))
    }

    @GetMapping("/preferences")
    fun getPreferences(
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<NotificationPreferenceDto> {
        val prefs = notificationService.getPreferences(principal.id)
        return ResponseEntity.ok(prefs)
    }

    @PatchMapping("/preferences")
    fun updatePreferences(
        @RequestBody request: UpdateNotificationPreferenceRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<NotificationPreferenceDto> {
        val user = userRepository.findById(principal.id).orElseThrow {
            IllegalArgumentException("User not found")
        }
        val prefs = notificationService.updatePreferences(user, request)
        return ResponseEntity.ok(prefs)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(e: IllegalArgumentException): ResponseEntity<Map<String, String>> {
        return ResponseEntity.badRequest().body(
            mapOf("error" to "BAD_REQUEST", "message" to (e.message ?: "Invalid request"))
        )
    }
}
