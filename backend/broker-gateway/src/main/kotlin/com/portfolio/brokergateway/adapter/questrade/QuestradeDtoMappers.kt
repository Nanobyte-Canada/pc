package com.portfolio.brokergateway.adapter.questrade

import com.portfolio.brokergateway.adapter.*

object QuestradeDtoMappers {

    fun mapAccountType(raw: String?): AccountType = when (raw?.trim()) {
        "Cash" -> AccountType.CASH
        "Margin" -> AccountType.MARGIN
        "TFSA" -> AccountType.TFSA
        "RRSP", "SRRSP", "LRRSP" -> AccountType.RRSP
        "FHSA" -> AccountType.FHSA
        "RESP", "FRESP" -> AccountType.RESP
        "LIRA" -> AccountType.LIRA
        "LIF" -> AccountType.LIF
        "RIF", "SRIF", "RRIF", "PRIF", "LRIF" -> AccountType.RIF
        else -> AccountType.OTHER
    }

    fun mapInstrumentType(typeCode: String?): InstrumentType = when (typeCode) {
        "Stock" -> InstrumentType.STOCK
        "ETF" -> InstrumentType.ETF
        "Option" -> InstrumentType.OPTION
        "Bond" -> InstrumentType.BOND
        "MutualFund" -> InstrumentType.MUTUAL_FUND
        else -> InstrumentType.OTHER
    }

    fun mapOrderStatus(state: String?): OrderStatus = when (state) {
        "Pending" -> OrderStatus.PENDING
        "Accepted", "Open" -> OrderStatus.SUBMITTED
        "Executed" -> OrderStatus.FILLED
        "PartiallyExecuted" -> OrderStatus.PARTIALLY_FILLED
        "Canceled", "Expired" -> OrderStatus.CANCELLED
        "Rejected" -> OrderStatus.REJECTED
        "Failed" -> OrderStatus.FAILED
        else -> OrderStatus.PENDING
    }

    fun mapOrderType(type: String?): OrderType = when (type) {
        "Market" -> OrderType.MARKET
        "Limit" -> OrderType.LIMIT
        "Stop" -> OrderType.STOP
        "StopLimit" -> OrderType.STOP_LIMIT
        else -> OrderType.MARKET
    }

    fun mapTimeInForce(tif: String?): TimeInForce = when (tif) {
        "Day" -> TimeInForce.DAY
        "GoodTillCanceled" -> TimeInForce.GTC
        "ImmediateOrCancel" -> TimeInForce.IOC
        "FillOrKill" -> TimeInForce.FOK
        else -> TimeInForce.DAY
    }

    fun mapOrderAction(side: String?): OrderAction = when (side) {
        "Buy", "BTO" -> OrderAction.BUY
        "Sell", "STC", "BTC" -> OrderAction.SELL
        else -> OrderAction.BUY
    }

    fun mapActivityType(type: String?, action: String?): ActivityType = when (type) {
        "Trades" -> if (action == "Buy") ActivityType.BUY else ActivityType.SELL
        "Dividends" -> ActivityType.DIVIDEND
        "Deposits" -> ActivityType.TRANSFER_IN
        "Withdrawals" -> ActivityType.TRANSFER_OUT
        "Fees", "FX conversion" -> ActivityType.FEE
        "Commissions" -> ActivityType.COMMISSION
        "Interest" -> ActivityType.INTEREST
        "Corporate actions" -> ActivityType.CORPORATE_ACTION
        else -> ActivityType.OTHER
    }
}
