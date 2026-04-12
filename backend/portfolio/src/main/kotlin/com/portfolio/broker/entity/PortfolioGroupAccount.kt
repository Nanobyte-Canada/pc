package com.portfolio.broker.entity

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(name = "portfolio_group_accounts")
class PortfolioGroupAccount(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    val group: PortfolioGroup,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connection_id", nullable = false)
    val connection: BrokerConnection,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now()
)
