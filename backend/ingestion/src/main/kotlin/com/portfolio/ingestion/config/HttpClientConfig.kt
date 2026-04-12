package com.portfolio.ingestion.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class HttpClientConfig {

    @Bean
    fun eodhdWebClient(props: IngestionProperties): WebClient =
        WebClient.builder()
            .baseUrl(props.eodhd.baseUrl)
            .defaultHeader("Accept", "application/json")
            .codecs { it.defaultCodecs().maxInMemorySize(16 * 1024 * 1024) }
            .build()
}
