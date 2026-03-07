package com.portfolio.ingestion.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime

enum class StepName {
    EODHD_UNIVERSE,
    AV_STOCK_INGESTION,       // Fetch raw data from AV API
    AV_ETF_INGESTION,         // Fetch raw data from AV API (deprecated)
    AV_STOCK_ENRICHMENT,      // Parse raw data to entity fields
    AV_ETF_ENRICHMENT,        // Parse raw data to entity fields (deprecated)
    ETFCOM_ETF_UNIVERSE,      // Fetch ETF universe from etf.com
    ETFCOM_ETF_ENRICHMENT     // Enrich ETFs from etf.com
}

enum class StepStatus {
    RUNNING, COMPLETED, FAILED, SKIPPED
}

@Entity
@Table(name = "ingestion_steps")
class IngestionStep(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    val run: IngestionRun,

    @Column(name = "step_name", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    val stepName: StepName,

    @Column(name = "started_at", nullable = false)
    val startedAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "completed_at")
    var completedAt: OffsetDateTime? = null,

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    var status: StepStatus = StepStatus.RUNNING,

    @Column(name = "records_processed")
    var recordsProcessed: Int = 0,

    @Column(name = "records_created")
    var recordsCreated: Int = 0,

    @Column(name = "records_updated")
    var recordsUpdated: Int = 0,

    @Column(name = "records_failed")
    var recordsFailed: Int = 0,

    @Column(name = "metadata", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    var metadata: String? = null,

    @OneToMany(mappedBy = "step", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val errors: MutableList<IngestionError> = mutableListOf()
) {
    fun complete(finalStatus: StepStatus) {
        this.status = finalStatus
        this.completedAt = OffsetDateTime.now()
    }

    fun updateCounts(processed: Int, created: Int, updated: Int, failed: Int) {
        this.recordsProcessed = processed
        this.recordsCreated = created
        this.recordsUpdated = updated
        this.recordsFailed = failed
    }
}
