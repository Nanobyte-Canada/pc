package com.portfolio.ingestion.persistence.entity

import com.fasterxml.jackson.databind.JsonNode
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDate

@Entity
@Table(name = "provider_config", schema = "ingestion")
class ProviderConfig(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int = 0,

    @Column(name = "provider_name", nullable = false, unique = true, length = 50)
    val providerName: String,

    @Column(nullable = false)
    var enabled: Boolean = true,

    @Column(nullable = false)
    var priority: Int = 0,

    @Column(name = "daily_quota")
    var dailyQuota: Int? = null,

    @Column(name = "requests_used_today", nullable = false)
    var requestsUsedToday: Int = 0,

    @Column(name = "last_quota_reset")
    var lastQuotaReset: LocalDate? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_json", columnDefinition = "jsonb")
    var configJson: JsonNode? = null
)
