package com.portfolio.broker.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "broker")
data class BrokerEncryptionConfig(
    val encryption: EncryptionConfig = EncryptionConfig()
)

data class EncryptionConfig(
    val secretKey: String = ""
)

@ConfigurationProperties(prefix = "broker.sync")
data class BrokerSyncConfig(
    val enabled: Boolean = false,
    val cron: String = "0 30 22 * * *"
)
