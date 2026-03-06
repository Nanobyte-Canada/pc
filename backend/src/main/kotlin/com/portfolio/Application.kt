package com.portfolio

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling
import com.portfolio.broker.config.BrokerEncryptionConfig
import com.portfolio.broker.config.SnapTradeConfig
import com.portfolio.ingestion.config.IngestionConfig

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(IngestionConfig::class, SnapTradeConfig::class, BrokerEncryptionConfig::class)
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}
