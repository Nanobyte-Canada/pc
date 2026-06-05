package com.portfolio.brokergateway.adapter

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "brokerType")
@JsonSubTypes(
    JsonSubTypes.Type(value = BrokerCredentials.IbkrCredentials::class, name = "IBKR"),
    JsonSubTypes.Type(value = BrokerCredentials.QuestradeCredentials::class, name = "QUESTRADE"),
    JsonSubTypes.Type(value = BrokerCredentials.WealthsimpleCredentials::class, name = "WEALTHSIMPLE")
)
sealed class BrokerCredentials {
    abstract val brokerType: BrokerType

    data class IbkrCredentials(
        val host: String,
        val port: Int,
        val clientId: Int
    ) : BrokerCredentials() {
        override val brokerType = BrokerType.IBKR
    }

    data class QuestradeCredentials(
        val refreshToken: String,
        val accessToken: String = "",
        val apiServerUrl: String = "",
        val expiresAtEpochSeconds: Long = 0,
        val usePractice: Boolean = false
    ) : BrokerCredentials() {
        override val brokerType = BrokerType.QUESTRADE
    }

    data class WealthsimpleCredentials(
        val accessToken: String,
        val refreshToken: String,
        val expiresAtEpochSeconds: Long,
        val email: String? = null,
        val passwordEncrypted: String? = null
    ) : BrokerCredentials() {
        override val brokerType = BrokerType.WEALTHSIMPLE
    }
}
