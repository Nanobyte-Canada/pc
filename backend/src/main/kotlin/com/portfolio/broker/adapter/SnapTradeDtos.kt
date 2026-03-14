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

// ========== Balance ==========

data class SnapTradeBalanceDto(
    val currency: String?,
    val cash: Double?
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
    val name: String?,
    val slug: String?,
    val logoUrl: String?,
    val description: String?
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
