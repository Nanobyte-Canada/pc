package com.portfolio.ingestion.persistence.repository

import com.portfolio.ingestion.persistence.entity.ProviderConfig
import org.springframework.data.jpa.repository.JpaRepository

interface ProviderConfigRepository : JpaRepository<ProviderConfig, Int> {
    fun findByProviderName(providerName: String): ProviderConfig?
}
