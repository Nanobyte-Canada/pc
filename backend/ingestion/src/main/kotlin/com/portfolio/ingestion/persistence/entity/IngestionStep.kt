package com.portfolio.ingestion.persistence.entity

import com.fasterxml.jackson.databind.JsonNode
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime

@Entity
@Table(name = "ingestion_steps", schema = "ingestion")
class IngestionStep(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    val run: IngestionRun,

    @Enumerated(EnumType.STRING)
    @Column(name = "step_name", nullable = false, length = 50)
    val stepName: StepName,

    @Column(name = "started_at", nullable = false)
    val startedAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "completed_at")
    var completedAt: OffsetDateTime? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: StepStatus = StepStatus.RUNNING,

    @Column(name = "records_processed", nullable = false)
    var recordsProcessed: Int = 0,

    @Column(name = "records_created", nullable = false)
    var recordsCreated: Int = 0,

    @Column(name = "records_updated", nullable = false)
    var recordsUpdated: Int = 0,

    @Column(name = "records_failed", nullable = false)
    var recordsFailed: Int = 0,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var metadata: JsonNode? = null,

    @OneToMany(mappedBy = "step", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val errors: MutableList<IngestionError> = mutableListOf()
)
