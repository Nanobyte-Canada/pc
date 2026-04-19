package com.portfolio.broker.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.OffsetDateTime

@Entity
@Table(name = "account_analytics")
class AccountAnalytics(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connection_id", nullable = false)
    val connection: BrokerConnection,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sector_exposure", columnDefinition = "jsonb")
    var sectorExposure: Map<String, Any?> = emptyMap(),

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "geography_exposure", columnDefinition = "jsonb")
    var geographyExposure: Map<String, Any?> = emptyMap(),

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "risk_profile", columnDefinition = "jsonb")
    var riskProfile: Map<String, Any?> = emptyMap(),

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "holdings", columnDefinition = "jsonb")
    var holdings: List<Map<String, Any?>> = emptyList(),

    @Column(name = "mer_weighted", precision = 8, scale = 4)
    var merWeighted: BigDecimal = BigDecimal.ZERO,

    @Column(name = "total_value", precision = 18, scale = 2)
    var totalValue: BigDecimal = BigDecimal.ZERO,

    @Column(name = "coverage_percent", precision = 5, scale = 2)
    var coveragePercent: BigDecimal = BigDecimal.ZERO,

    @Column(name = "positions_count")
    var positionsCount: Int = 0,

    @Column(name = "irr", precision = 10, scale = 4)
    var irr: BigDecimal? = null,

    @Column(name = "computed_at", nullable = false)
    var computedAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
)
