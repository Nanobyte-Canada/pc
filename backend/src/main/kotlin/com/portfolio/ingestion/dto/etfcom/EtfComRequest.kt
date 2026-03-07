package com.portfolio.ingestion.dto.etfcom

data class EtfComRequest(
    val query: String,
    val variables: EtfComRequestVariables
)

data class EtfComRequestVariables(
    val fund_id: String = "0",
    val ticker: String,
    val fund_isin: String = ""
)
