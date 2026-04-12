package com.portfolio.broker.entity

import com.portfolio.auth.entity.User
import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(name = "portfolio_groups")
class PortfolioGroup(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(name = "name", nullable = false, length = 100)
    var name: String,

    @Column(name = "description", columnDefinition = "TEXT")
    var description: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),

    @OneToMany(mappedBy = "group", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    val targets: MutableList<PortfolioTarget> = mutableListOf(),

    @OneToMany(mappedBy = "group", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    val linkedAccounts: MutableList<PortfolioGroupAccount> = mutableListOf(),

    @OneToOne(mappedBy = "group", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    var settings: PortfolioGroupSettings? = null,

    @OneToMany(mappedBy = "group", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    val excludedAssets: MutableList<PortfolioExcludedAsset> = mutableListOf(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_portfolio_id")
    var modelPortfolio: ModelPortfolio? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "benchmark_model_id")
    var benchmarkModel: ModelPortfolio? = null
)
