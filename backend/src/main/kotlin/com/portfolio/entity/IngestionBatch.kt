package com.portfolio.entity

import jakarta.persistence.*
import java.time.LocalDate
import java.time.OffsetDateTime

enum class IngestionStatus {
    PENDING, PROCESSING, COMPLETED, FAILED
}

@Entity
@Table(name = "ingestion_batches")
class IngestionBatch(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id", nullable = false)
    val source: DataSource,

    @Column(name = "batch_date", nullable = false)
    val batchDate: LocalDate,

    @Column(name = "ingested_at", nullable = false)
    val ingestedAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "record_count")
    val recordCount: Int? = null,

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    val status: IngestionStatus = IngestionStatus.COMPLETED,

    @Column(name = "metadata", columnDefinition = "JSONB")
    val metadata: String? = null
)
