package com.portfolio.brokergateway.adapter

data class BrokerCapabilities(
    val brokerType: BrokerType,
    val supportsOrders: Boolean,
    val supportedOrderTypes: List<OrderType>,
    val supportsOptionPositions: Boolean,
    val supportsFractionalShares: Boolean,
    val supportsRealTimeData: Boolean,
    val supportsHistoricalActivities: Boolean,
    val activityHistoryDepth: String?,
    val orderRateLimit: String?,
    val isOfficialApi: Boolean,
    val notes: String?
)

enum class OrderType {
    MARKET, LIMIT, STOP, STOP_LIMIT
}

enum class OrderAction {
    BUY, SELL
}

enum class TimeInForce {
    DAY, GTC, IOC, FOK
}

enum class OrderStatus {
    PENDING, SUBMITTED, FILLED, PARTIALLY_FILLED, CANCELLED, REJECTED, FAILED
}

enum class ActivityType {
    BUY, SELL, DIVIDEND, TRANSFER_IN, TRANSFER_OUT,
    FEE, COMMISSION, INTEREST,
    OPTION_EXPIRATION, OPTION_ASSIGNMENT, OPTION_EXERCISE,
    STOCK_SPLIT, CORPORATE_ACTION, OTHER
}

enum class InstrumentType {
    STOCK, ETF, MUTUAL_FUND, OPTION, BOND, CASH, CRYPTO, OTHER
}

enum class AccountType {
    CASH, MARGIN, TFSA, RRSP, FHSA, RESP, LIRA, LIF, RIF, CRYPTO, OTHER
}
