package com.portfolio.broker.entity

import com.portfolio.auth.entity.User
import jakarta.persistence.*
import java.time.OffsetDateTime

enum class WidgetKey {
    PORTFOLIO_VALUE,
    AVAILABLE_CASH,
    BUYING_POWER,
    RISK_PROFILE,
    SECTOR_EXPOSURE,
    GEOGRAPHY_EXPOSURE,
    REBALANCING_PROGRESS,
    PENDING_ORDERS,
    OPEN_ORDERS,
    FEES_COMMISSION,
    DIVIDEND_CALENDAR,
    POSITIONS_TABLE,
    HOLDINGS_TABLE,
    CONNECTED_ACCOUNTS,
    POSITIONS_HOLDINGS,
    PORTFOLIO_SUMMARY,
    // Legacy keys kept for backward compatibility with existing DB rows
    POSITIONS_SUMMARY,
    HOLDINGS_COUNT,
    REFRESH_BUTTON
}

enum class DashboardContextType {
    DASHBOARD, ACCOUNT
}

@Entity
@Table(name = "dashboard_preferences")
class DashboardPreference(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Enumerated(EnumType.STRING)
    @Column(name = "context_type", nullable = false, length = 20)
    val contextType: DashboardContextType = DashboardContextType.DASHBOARD,

    @Column(name = "context_id")
    val contextId: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "widget_key", nullable = false, length = 50)
    val widgetKey: WidgetKey,

    @Column(name = "is_visible", nullable = false)
    var isVisible: Boolean = true,

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0,

    @Column(name = "column_span", nullable = false)
    var columnSpan: Int = 1,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
)
