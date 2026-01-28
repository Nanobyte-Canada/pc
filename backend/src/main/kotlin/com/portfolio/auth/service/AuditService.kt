package com.portfolio.auth.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.portfolio.auth.entity.AuditEventType
import com.portfolio.auth.entity.AuditLog
import com.portfolio.auth.entity.User
import com.portfolio.auth.repository.AuditLogRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class AuditService(
    private val auditLogRepository: AuditLogRepository,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun log(
        eventType: AuditEventType,
        user: User? = null,
        ipAddress: String? = null,
        userAgent: String? = null,
        resourceType: String? = null,
        resourceId: String? = null,
        details: Map<String, Any>? = null,
        success: Boolean = true,
        errorMessage: String? = null
    ) {
        try {
            val auditLog = AuditLog(
                user = user,
                eventType = eventType,
                ipAddress = ipAddress,
                userAgent = userAgent,
                resourceType = resourceType,
                resourceId = resourceId,
                details = details?.let { objectMapper.writeValueAsString(it) },
                success = success,
                errorMessage = errorMessage
            )
            auditLogRepository.save(auditLog)
        } catch (e: Exception) {
            logger.error("Failed to log audit event: $eventType", e)
        }
    }

    fun logLogin(user: User, ipAddress: String?, userAgent: String?, success: Boolean = true, errorMessage: String? = null) {
        log(
            eventType = if (success) AuditEventType.AUTH_LOGIN else AuditEventType.AUTH_FAILED_LOGIN,
            user = user,
            ipAddress = ipAddress,
            userAgent = userAgent,
            success = success,
            errorMessage = errorMessage
        )
    }

    fun logLogout(user: User, ipAddress: String?, userAgent: String?) {
        log(
            eventType = AuditEventType.AUTH_LOGOUT,
            user = user,
            ipAddress = ipAddress,
            userAgent = userAgent
        )
    }

    fun logSignup(user: User, ipAddress: String?, userAgent: String?) {
        log(
            eventType = AuditEventType.AUTH_SIGNUP,
            user = user,
            ipAddress = ipAddress,
            userAgent = userAgent
        )
    }

    fun logPasswordResetRequest(user: User, ipAddress: String?, userAgent: String?) {
        log(
            eventType = AuditEventType.PASSWORD_RESET_REQUEST,
            user = user,
            ipAddress = ipAddress,
            userAgent = userAgent
        )
    }

    fun logPasswordResetComplete(user: User, ipAddress: String?, userAgent: String?) {
        log(
            eventType = AuditEventType.PASSWORD_RESET_COMPLETE,
            user = user,
            ipAddress = ipAddress,
            userAgent = userAgent
        )
    }

    fun logEmailVerification(user: User, ipAddress: String?, userAgent: String?) {
        log(
            eventType = AuditEventType.EMAIL_VERIFICATION,
            user = user,
            ipAddress = ipAddress,
            userAgent = userAgent
        )
    }

    fun logOAuthLink(user: User, provider: String, ipAddress: String?, userAgent: String?) {
        log(
            eventType = AuditEventType.OAUTH_LINK,
            user = user,
            ipAddress = ipAddress,
            userAgent = userAgent,
            details = mapOf("provider" to provider)
        )
    }

    fun logUserLock(user: User, reason: String, ipAddress: String?) {
        log(
            eventType = AuditEventType.USER_LOCK,
            user = user,
            ipAddress = ipAddress,
            details = mapOf("reason" to reason)
        )
    }

    fun logPasswordChange(user: User, ipAddress: String?, userAgent: String?) {
        log(
            eventType = AuditEventType.PASSWORD_CHANGE,
            user = user,
            ipAddress = ipAddress,
            userAgent = userAgent
        )
    }

    fun logProfileUpdate(user: User, ipAddress: String?, userAgent: String?) {
        log(
            eventType = AuditEventType.PROFILE_UPDATE,
            user = user,
            ipAddress = ipAddress,
            userAgent = userAgent
        )
    }
}
