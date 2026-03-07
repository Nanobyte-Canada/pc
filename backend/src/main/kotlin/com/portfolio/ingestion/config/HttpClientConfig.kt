package com.portfolio.ingestion.config

import io.netty.channel.ChannelOption
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration
import java.util.concurrent.TimeUnit

@Configuration
class HttpClientConfig(
    private val ingestionConfig: IngestionConfig
) {

    private fun createHttpClient(): HttpClient {
        return HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
            .responseTimeout(Duration.ofSeconds(30))
            .doOnConnected { conn ->
                conn.addHandlerLast(ReadTimeoutHandler(30, TimeUnit.SECONDS))
                conn.addHandlerLast(WriteTimeoutHandler(30, TimeUnit.SECONDS))
            }
    }

    private fun createHttpClientWithExtendedTimeouts(): HttpClient {
        val sslContext = SslContextBuilder
            .forClient()
            .trustManager(InsecureTrustManagerFactory.INSTANCE)
            .build()

        return HttpClient.create()
            .secure { spec -> spec.sslContext(sslContext) }
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30_000)
            .responseTimeout(Duration.ofSeconds(60))
            .doOnConnected { conn ->
                conn.addHandlerLast(ReadTimeoutHandler(60, TimeUnit.SECONDS))
                conn.addHandlerLast(WriteTimeoutHandler(60, TimeUnit.SECONDS))
            }
    }

    @Bean
    fun eodhdWebClient(): WebClient {
        return WebClient.builder()
            .baseUrl(ingestionConfig.eodhd.baseUrl)
            .clientConnector(ReactorClientHttpConnector(createHttpClient()))
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .codecs { configurer ->
                configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024) // 16MB
            }
            .build()
    }

    @Bean
    fun alphaVantageWebClient(): WebClient {
        return WebClient.builder()
            .baseUrl(ingestionConfig.alphavantage.baseUrl)
            .clientConnector(ReactorClientHttpConnector(createHttpClientWithExtendedTimeouts()))
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .codecs { configurer ->
                configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024) // 16MB
            }
            .build()
    }

    @Bean
    fun etfComWebClient(): WebClient {
        return WebClient.builder()
            .baseUrl(ingestionConfig.etfcom.baseUrl)
            .clientConnector(ReactorClientHttpConnector(createHttpClient()))
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .codecs { configurer ->
                configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024) // 16MB
            }
            .build()
    }
}
