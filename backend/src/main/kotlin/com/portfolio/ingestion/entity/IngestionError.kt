package com.portfolio.ingestion.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime

enum class ErrorType {
    API_ERROR, PARSE_ERROR, DB_ERROR, RATE_LIMIT, VALIDATION_ERROR, AMBIGUOUS_MATCH, NOT_FOUND, DUPLICATE_ISIN
}

@Entity
@Table(name = "ingestion_errors")
class IngestionError(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "step_id", nullable = false)
    val step: IngestionStep,

    @Column(name = "error_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    val errorType: ErrorType,

    @Column(name = "error_code", length = 50)
    val errorCode: String? = null,

    @Column(name = "error_message", columnDefinition = "TEXT")
    val errorMessage: String? = null,

    @Column(name = "context", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    val context: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now()
)
