package com.portfolio.ingestion.persistence.entity

enum class InstrumentType {
    STOCK, PREFERRED_STOCK, ETF, MUTUAL_FUND, INDEX, BOND
}

enum class InstrumentStatus {
    ACTIVE, DELISTED, SUSPENDED, PENDING
}

enum class RunType {
    SCHEDULED, MANUAL
}

enum class RunStatus {
    RUNNING, COMPLETED, FAILED, PARTIAL
}

enum class StepName {
    EXCHANGE_SYNC, UNIVERSE_SYNC, RAW_DATA_FETCH
}

enum class StepStatus {
    RUNNING, COMPLETED, FAILED, SKIPPED
}

enum class ErrorType {
    API_ERROR, PARSE_ERROR, DB_ERROR, RATE_LIMIT, VALIDATION_ERROR, DUPLICATE_ISIN, NOT_FOUND
}
