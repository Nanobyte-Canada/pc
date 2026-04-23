package com.portfolio.brokergateway.adapter.ibkr

import com.portfolio.brokergateway.adapter.*

object IbkrDtoMappers {

    fun mapAccountType(raw: String?): AccountType = when (raw?.trim()) {
        "Individual", "Cash" -> AccountType.CASH
        "Margin" -> AccountType.MARGIN
        "TFSA" -> AccountType.TFSA
        "RRSP" -> AccountType.RRSP
        "FHSA" -> AccountType.FHSA
        "RESP" -> AccountType.RESP
        "LIRA" -> AccountType.LIRA
        "LIF" -> AccountType.LIF
        "RIF", "RRIF" -> AccountType.RIF
        else -> AccountType.OTHER
    }

    fun mapInstrumentType(secType: String?): InstrumentType = when (secType?.uppercase()) {
        "STK" -> InstrumentType.STOCK
        "OPT" -> InstrumentType.OPTION
        "BOND" -> InstrumentType.BOND
        "FUND" -> InstrumentType.MUTUAL_FUND
        "CASH" -> InstrumentType.CASH
        "CRYPTO" -> InstrumentType.CRYPTO
        else -> InstrumentType.OTHER
    }

    fun mapOrderStatus(status: String?): OrderStatus = when (status) {
        "PendingSubmit", "PendingCancel" -> OrderStatus.PENDING
        "Submitted", "PreSubmitted" -> OrderStatus.SUBMITTED
        "Filled" -> OrderStatus.FILLED
        "Cancelled", "ApiCancelled" -> OrderStatus.CANCELLED
        "Inactive" -> OrderStatus.REJECTED
        "Error" -> OrderStatus.FAILED
        else -> OrderStatus.PENDING
    }

    fun mapActivityType(code: String?): ActivityType = when (code?.uppercase()) {
        "BUY", "BOT" -> ActivityType.BUY
        "SELL", "SLD" -> ActivityType.SELL
        "DIV", "CDIV" -> ActivityType.DIVIDEND
        "DEP" -> ActivityType.TRANSFER_IN
        "WITH" -> ActivityType.TRANSFER_OUT
        "COMM", "OTHER_FEE" -> ActivityType.FEE
        "INT" -> ActivityType.INTEREST
        "EXP" -> ActivityType.OPTION_EXPIRATION
        "ASSIGN" -> ActivityType.OPTION_ASSIGNMENT
        "EXER" -> ActivityType.OPTION_EXERCISE
        "SPLIT" -> ActivityType.STOCK_SPLIT
        "CA" -> ActivityType.CORPORATE_ACTION
        else -> ActivityType.OTHER
    }

    fun mapOptionRight(right: String?): String? = when (right?.uppercase()) {
        "C" -> "CALL"
        "P" -> "PUT"
        "", null -> null
        else -> right
    }
}
