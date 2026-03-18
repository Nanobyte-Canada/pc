package com.portfolio.ingestion.dto.etfcom

import com.fasterxml.jackson.databind.JsonNode

/**
 * Combined enrichment data for a single ETF.
 * rawPayloads holds the full JSON from each of the 5 etf.com API queries,
 * keyed by query name. Values are parsed JsonNode objects so Jackson serialises
 * them as proper nested JSON (not escaped strings).
 */
data class EtfComEnrichmentData(
    val rawPayloads: Map<String, JsonNode>
)
