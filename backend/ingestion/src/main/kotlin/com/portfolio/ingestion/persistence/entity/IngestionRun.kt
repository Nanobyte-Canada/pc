package com.portfolio.ingestion.persistence.entity

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(name = "ingestion_runs", schema = "ingestion")
class IngestionRun(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(name = "run_type", nullable = false, length = 20)
    val runType: RunType,

    @Column(name = "started_at", nullable = false)
    val startedAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "completed_at")
    var completedAt: OffsetDateTime? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: RunStatus = RunStatus.RUNNING,

    @Column(name = "trigger_source", length = 100)
    val triggerSource: String? = null,

    @OneToMany(mappedBy = "run", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val steps: MutableList<IngestionStep> = mutableListOf()
)
