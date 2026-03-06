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
