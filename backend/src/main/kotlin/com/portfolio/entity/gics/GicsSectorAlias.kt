package com.portfolio.entity.gics

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(name = "gics_sector_aliases")
class GicsSectorAlias(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "alias_value", nullable = false, length = 100)
    val aliasValue: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gics_sector_id", nullable = false)
    val gicsSector: GicsSector,

    @Column(name = "source", nullable = false, length = 30)
    val source: String = "SEEKING_ALPHA",

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now()
)
