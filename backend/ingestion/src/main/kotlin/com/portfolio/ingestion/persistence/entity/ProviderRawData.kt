package com.portfolio.ingestion.persistence.entity

import com.fasterxml.jackson.databind.JsonNode
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime

@Entity
@Table(
    name = "provider_raw_data", schema = "ingestion",
    uniqueConstraints = [UniqueConstraint(columnNames = ["instrument_id", "provider", "data_type"])]
)
class ProviderRawData(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrument_id", nullable = false)
    val instrument: Instrument,

    @Column(nullable = false, length = 50)
    val provider: String,

    @Column(name = "data_type", nullable = false, length = 30)
    val dataType: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", nullable = false, columnDefinition = "jsonb")
    var rawPayload: JsonNode,

    @Column(name = "payload_hash", length = 64)
    var payloadHash: String? = null,

    @Column(name = "fetched_at", nullable = false)
    var fetchedAt: OffsetDateTime = OffsetDateTime.now()
)
