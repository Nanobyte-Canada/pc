package com.portfolio.brokergateway.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "broker-gateway")
data class GatewayProperties(
    val encryption: EncryptionProperties = EncryptionProperties(),
    val serviceAuth: ServiceAuthProperties = ServiceAuthProperties()
)

data class EncryptionProperties(
    val secretKey: String = ""
)

data class ServiceAuthProperties(
    val apiKey: String = "dev-gateway-key"
)
