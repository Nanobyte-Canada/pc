package com.portfolio.ingestion.entity

import jakarta.persistence.*
import java.time.OffsetDateTime

enum class RunType {
    SCHEDULED, MANUAL
}

enum class RunStatus {
    RUNNING, COMPLETED, FAILED, PARTIAL
}

@Entity
@Table(name = "ingestion_runs")
class IngestionRun(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "run_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    val runType: RunType,

    @Column(name = "started_at", nullable = false)
    val startedAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "completed_at")
    var completedAt: OffsetDateTime? = null,

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    var status: RunStatus = RunStatus.RUNNING,

    @Column(name = "trigger_source", length = 50)
    val triggerSource: String? = null,

    @OneToMany(mappedBy = "run", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val steps: MutableList<IngestionStep> = mutableListOf()
) {
    fun complete(finalStatus: RunStatus) {
        this.status = finalStatus
        this.completedAt = OffsetDateTime.now()
    }
}
