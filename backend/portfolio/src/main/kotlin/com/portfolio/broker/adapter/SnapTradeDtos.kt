package com.portfolio.broker.adapter

import java.time.LocalDate
import java.util.UUID

// ========== Account ==========

data class SnapTradeAccountDto(
    val id: UUID?,
    val brokerageAuthorization: UUID?,
    val number: String?,
    val name: String?,
    val institutionName: String?,
    val currency: String?,
    val metaType: String? = null,
    val metaAccountNumber: String? = null,
    val rawType: String? = null,
    val balance: Double? = null,
    val balanceCurrency: String? = null
)

// ========== Connection / BrokerageAuthorization ==========

data class SnapTradeConnectionDto(
    val id: UUID?,
    val disabled: Boolean?,
    val brokerageName: String?,
    val brokerLogoUrl: String?,
    val type: String?
)

// ========== Position ==========

data class SnapTradePositionDto(
    val symbol: String?,
    val symbolId: String?,
    val symbolDescription: String?,
    val symbolTypeCode: String?,
    val currencyCode: String?,
    val units: Double?,
    val price: Double?,
    val averagePurchasePrice: Double?
)

// ========== Option Position ==========

data class SnapTradeOptionPositionDto(
    val symbol: String?,
    val strikePrice: Double?,
    val expirationDate: java.time.LocalDate?,
    val optionType: String?,       // CALL or PUT
    val underlyingSymbol: String?,
    val units: Double?,
    val price: Double?,
    val averagePurchasePrice: Double?,
    val currencyCode: String?
)

// ========== Holdings (positions + balances + total) ==========

data class SnapTradeHoldingsDto(
    val totalValue: Double?,
    val totalValueCurrency: String?,
    val positions: List<SnapTradePositionDto>,
    val balances: List<SnapTradeBalanceDto>
)

// ========== Balance ==========

data class SnapTradeBalanceDto(
    val currency: String?,
    val cash: Double?,
    val buyingPower: Double? = null
)

// ========== Activity ==========

data class SnapTradeActivityDto(
    val id: String?,
    val type: String?,
    val symbol: String?,
    val description: String?,
    val units: Double?,
    val price: Double?,
    val amount: Double?,
    val fee: Double?,
    val currency: String?,
    val tradeDate: LocalDate?,
    val settlementDate: LocalDate?,
    val optionType: String?,
    val rawJson: String?
)

// ========== Brokerage ==========

data class SnapTradeBrokerageDto(
    val id: UUID? = null,
    val name: String?,
    val slug: String?,
    val displayName: String? = null,
    val logoUrl: String?,
    val description: String?,
    val url: String? = null,
    val openUrl: String? = null,
    val enabled: Boolean? = null,
    val maintenanceMode: Boolean? = null,
    val isDegraded: Boolean? = null,
    val allowsTrading: Boolean? = null,
    val allowsFractionalUnits: Boolean? = null,
    val hasReporting: Boolean? = null,
    val isRealTimeConnection: Boolean? = null,
    val brokerageType: BrokerageTypeDto? = null
)

data class BrokerageTypeDto(
    val id: UUID?,
    val name: String?
)

// ========== Brokerage Authorization Type ==========

data class SnapTradeBrokerageAuthTypeDto(
    val id: UUID?,
    val type: String?,      // "read" or "trade"
    val authType: String?,  // "OAUTH", "SCRAPE", "UNOFFICIAL_API"
    val brokerageId: UUID?
)

// ========== Order ==========

data class SnapTradeOrderDto(
    val brokerageOrderId: String?,
    val status: String?,
    val symbol: String?,
    val action: String?,
    val units: Double?,
    val price: Double?
)

// ========== Account Order (for listing existing broker orders) ==========

data class SnapTradeAccountOrderDto(
    val brokerageOrderId: String?,
    val status: String?,
    val symbol: String?,
    val action: String?,
    val totalQuantity: Double?,
    val openQuantity: Double?,
    val filledQuantity: Double?,
    val executionPrice: Double?,
    val limitPrice: Double?,
    val stopPrice: Double?,
    val orderType: String?,
    val timeInForce: String?,
    val timePlaced: String?,
    val timeUpdated: String?,
    val timeExecuted: String?,
    val currency: String?
)

// ========== API Status ==========

data class SnapTradeApiStatusDto(
    val online: Boolean,
    val version: String?
)

// ========== Exception ==========

class SnapTradeApiException(
    val errorCode: Int?,
    message: String?,
    cause: Throwable? = null
) : RuntimeException(message, cause) {

    companion object {
        const val ERROR_PERSONAL_KEY_SLOT_OCCUPIED = 1012

        fun extractErrorCode(message: String?): Int? {
            if (message == null) return null
            val match = Regex("(\\d{4})").find(message)
            return match?.value?.toIntOrNull()
        }
    }
}
