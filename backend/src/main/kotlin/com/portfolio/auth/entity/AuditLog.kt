package com.portfolio.auth.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime

enum class AuditEventType {
    AUTH_LOGIN,
    AUTH_LOGOUT,
    AUTH_SIGNUP,
    AUTH_FAILED_LOGIN,
    PASSWORD_RESET_REQUEST,
    PASSWORD_RESET_COMPLETE,
    PASSWORD_CHANGE,
    EMAIL_VERIFICATION,
    EMAIL_CHANGE,
    PROFILE_UPDATE,
    OAUTH_LINK,
    OAUTH_UNLINK,
    ROLE_GRANT,
    ROLE_REVOKE,
    USER_LOCK,
    USER_UNLOCK,
    USER_SUSPEND,
    // Broker integration events
    BROKER_CONNECT,
    BROKER_DISCONNECT,
    BROKER_FETCH_POSITIONS,
    BROKER_PREFS_UPDATE,
    BROKER_TOKEN_REFRESH,
    BROKER_FETCH_ERROR
}

@Entity
@Table(name = "audit_log")
class AuditLog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    val user: User? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    val eventType: AuditEventType,

    @Column(name = "event_subtype", length = 50)
    val eventSubtype: String? = null,

    @Column(name = "ip_address", length = 45)
    val ipAddress: String? = null,

    @Column(name = "user_agent", length = 500)
    val userAgent: String? = null,

    @Column(name = "resource_type", length = 50)
    val resourceType: String? = null,

    @Column(name = "resource_id", length = 100)
    val resourceId: String? = null,

    @Column(name = "details", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    val details: String? = null,

    @Column(name = "success", nullable = false)
    val success: Boolean = true,

    @Column(name = "error_message", length = 500)
    val errorMessage: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now()
)
