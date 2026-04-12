package com.portfolio.ingestion.provider.eodhd

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class EodhdExchangeDto(
    @JsonProperty("Code") val code: String,
    @JsonProperty("Name") val name: String,
    @JsonProperty("Country") val country: String?,
    @JsonProperty("Currency") val currency: String?,
    @JsonProperty("OperatingMIC") val operatingMic: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EodhdSymbolDto(
    @JsonProperty("Code") val code: String,
    @JsonProperty("Name") val name: String?,
    @JsonProperty("Country") val country: String?,
    @JsonProperty("Exchange") val exchange: String?,
    @JsonProperty("Currency") val currency: String?,
    @JsonProperty("Type") val type: String?,
    @JsonProperty("Isin") val isin: String?
)
