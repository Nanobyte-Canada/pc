package com.portfolio.brokergateway.adapter.dto

import com.portfolio.brokergateway.adapter.AccountType
import com.portfolio.brokergateway.adapter.BrokerType

data class UnifiedAccount(
    val accountId: String,
    val accountNumber: String?,
    val accountName: String?,
    val accountType: AccountType,
    val currency: String?,
    val brokerType: BrokerType,
    val status: String?
)
