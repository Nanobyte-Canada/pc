package com.portfolio.broker.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "snaptrade")
data class SnapTradeConfig(
    val clientId: String = "",
    val consumerKey: String = "",
    val redirectUri: String = "http://localhost:3000/brokers/connections"
)

@ConfigurationProperties(prefix = "broker")
data class BrokerEncryptionConfig(
    val encryption: EncryptionConfig = EncryptionConfig()
)

data class EncryptionConfig(
    val secretKey: String = ""
)

@ConfigurationProperties(prefix = "snaptrade.health-check")
data class SnapTradeHealthConfig(
    val enabled: Boolean = false,
    val cron: String = "0 */15 * * * *"
)

@ConfigurationProperties(prefix = "broker.sync")
data class BrokerSyncConfig(
    val enabled: Boolean = false,
    val cron: String = "0 30 22 * * *"
)
