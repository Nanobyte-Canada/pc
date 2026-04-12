package com.portfolio.dto.response

data class ScreenerSearchResultDto(
    val id: Long,
    val type: String,
    val ticker: String,
    val name: String,
    val exchange: String?,
    val matchType: String
)

data class ScreenerSearchMetaDto(
    val query: String,
    val resultCount: Int,
    val searchTimeMs: Long
)

data class ScreenerSearchResponseDto(
    val data: List<ScreenerSearchResultDto>,
    val meta: ScreenerSearchMetaDto
)
