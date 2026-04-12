package com.portfolio.broker.entity

import com.portfolio.auth.entity.User
import jakarta.persistence.*
import java.time.OffsetDateTime

enum class RiskLevel {
    LOW, MODERATE, HIGH, EXTRA_HIGH
}

@Entity
@Table(name = "model_portfolios")
class ModelPortfolio(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "name", nullable = false, length = 100)
    var name: String,

    @Column(name = "description", columnDefinition = "TEXT")
    var description: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 20)
    var riskLevel: RiskLevel,

    @Column(name = "is_system", nullable = false)
    val isSystem: Boolean = false,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    val user: User? = null,

    @OneToMany(mappedBy = "modelPortfolio", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    val allocations: MutableList<ModelPortfolioAllocation> = mutableListOf(),

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
)
