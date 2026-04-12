package com.portfolio.dto.response

import java.time.OffsetDateTime

data class ErrorResponse(
    val status: Int,
    val error: String,
    val message: String,
    val errorCode: String? = null,
    val field: String? = null,
    val timestamp: OffsetDateTime = OffsetDateTime.now(),
    val path: String? = null
)
