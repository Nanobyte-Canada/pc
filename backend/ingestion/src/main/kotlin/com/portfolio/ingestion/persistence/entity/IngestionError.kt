package com.portfolio.ingestion.persistence.entity

import com.fasterxml.jackson.databind.JsonNode
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime

@Entity
@Table(name = "ingestion_errors", schema = "ingestion")
class IngestionError(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "step_id", nullable = false)
    val step: IngestionStep,

    @Enumerated(EnumType.STRING)
    @Column(name = "error_type", nullable = false, length = 30)
    val errorType: ErrorType,

    @Column(name = "error_code", length = 50)
    val errorCode: String? = null,

    @Column(name = "error_message", columnDefinition = "text")
    val errorMessage: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    val context: JsonNode? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now()
)
