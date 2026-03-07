package com.portfolio.ingestion.dto.etfcom

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class EtfComTickerDto(
    @JsonProperty("fundId")
    val fundId: Int,

    @JsonProperty("fund")
    val fund: String?,

    @JsonProperty("ticker")
    val ticker: String,

    @JsonProperty("inceptionDate")
    val inceptionDate: String?,

    @JsonProperty("assetClass")
    val assetClass: String?,

    @JsonProperty("issuer")
    val issuer: String?
)
