package com.portfolio.ingestion.persistence.entity

import jakarta.persistence.*

@Entity
@Table(
    name = "instrument_exchanges", schema = "ingestion",
    uniqueConstraints = [UniqueConstraint(columnNames = ["instrument_id", "exchange_id"])]
)
class InstrumentExchange(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrument_id", nullable = false)
    val instrument: Instrument,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exchange_id", nullable = false)
    val exchange: Exchange,

    @Column(name = "local_ticker", length = 20)
    var localTicker: String? = null,

    @Column(name = "is_primary", nullable = false)
    var isPrimary: Boolean = false
)
