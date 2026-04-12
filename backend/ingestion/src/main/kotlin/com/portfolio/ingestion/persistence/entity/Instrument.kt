package com.portfolio.ingestion.persistence.entity

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(name = "instruments", schema = "ingestion")
class Instrument(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, length = 50)
    var ticker: String,

    @Column(nullable = false, length = 500)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "instrument_type", nullable = false, length = 20)
    var instrumentType: InstrumentType,

    @Column(length = 12, unique = true)
    var isin: String? = null,

    @Column(length = 9)
    var cusip: String? = null,

    @Column(length = 10)
    var currency: String? = null,

    @Column(length = 50)
    var country: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: InstrumentStatus = InstrumentStatus.ACTIVE,

    @Column(name = "source_last_seen_at")
    var sourceLastSeenAt: OffsetDateTime? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
)
