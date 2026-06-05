// adapter/wealthsimple/WealthsimpleConfig.kt
package com.portfolio.brokergateway.adapter.wealthsimple

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "broker-gateway.wealthsimple")
data class WealthsimpleConfig(
    val enabled: Boolean = false,
    val authUrl: String = "https://api.production.wealthsimple.com/v1/oauth/v2/token",
    val graphqlUrl: String = "https://my.wealthsimple.com/graphql",
    val clientId: String = "4da53ac2b03225bed1550eba8e4611e086c7b905a3855571f1c77e1bbdc5f62b",
    val orderRateLimitPerHour: Int = 7
)
