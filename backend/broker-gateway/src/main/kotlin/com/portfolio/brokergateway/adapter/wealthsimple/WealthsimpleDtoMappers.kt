// adapter/wealthsimple/WealthsimpleDtoMappers.kt
package com.portfolio.brokergateway.adapter.wealthsimple

import com.portfolio.brokergateway.adapter.*

object WealthsimpleDtoMappers {

    fun mapAccountType(raw: String?): AccountType = when (raw?.lowercase()) {
        "ca_non_registered" -> AccountType.CASH
        "ca_non_registered_margin" -> AccountType.MARGIN
        "ca_tfsa" -> AccountType.TFSA
        "ca_rrsp" -> AccountType.RRSP
        "ca_fhsa" -> AccountType.FHSA
        "ca_lira" -> AccountType.LIRA
        "ca_crypto" -> AccountType.CRYPTO
        else -> AccountType.OTHER
    }

    fun mapInstrumentType(securityType: String?): InstrumentType = when (securityType?.lowercase()) {
        "equity" -> InstrumentType.STOCK
        "etf" -> InstrumentType.ETF
        "mutual_fund" -> InstrumentType.MUTUAL_FUND
        "crypto" -> InstrumentType.CRYPTO
        else -> InstrumentType.OTHER
    }

    fun mapOrderStatus(status: String?): OrderStatus = when (status?.lowercase()) {
        "submitted" -> OrderStatus.PENDING
        "posted" -> OrderStatus.SUBMITTED
        "filled" -> OrderStatus.FILLED
        "partial_fill" -> OrderStatus.PARTIALLY_FILLED
        "cancelled" -> OrderStatus.CANCELLED
        "rejected" -> OrderStatus.REJECTED
        "failed" -> OrderStatus.FAILED
        else -> OrderStatus.PENDING
    }

    fun mapActivityType(type: String?): ActivityType = when (type?.lowercase()) {
        "buy" -> ActivityType.BUY
        "sell" -> ActivityType.SELL
        "dividend" -> ActivityType.DIVIDEND
        "deposit", "institutional_transfer" -> ActivityType.TRANSFER_IN
        "withdrawal" -> ActivityType.TRANSFER_OUT
        "fee" -> ActivityType.FEE
        "interest" -> ActivityType.INTEREST
        "stock_split" -> ActivityType.STOCK_SPLIT
        "reorganization" -> ActivityType.CORPORATE_ACTION
        "refund" -> ActivityType.OTHER
        else -> ActivityType.OTHER
    }
}
